package com.leydata.orchestrator.consent;

import com.leydata.orchestrator.config.ConsentCacheProperties;
import com.leydata.orchestrator.consent.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsentService {

    private final ReactiveStringRedisTemplate redis;
    private final WebClient leydataClient;
    private final ConsentCacheProperties cacheProps;
    private final ObjectMapper objectMapper;

    // ── CHECK ─────────────────────────────────────────────────────────────────

    public Mono<ConsentCheckResponse> check(String subjectId, UUID purposeId) {
        String key = cacheKey(subjectId, purposeId);

        return redis.opsForValue().get(key)
                .flatMap(json -> {
                    try {
                        return Mono.just(objectMapper.readValue(json, ConsentCheckResponse.class));
                    } catch (Exception e) {
                        // Clave en formato antiguo (string plano) o corrupta → tratar como miss
                        log.debug("Cache hit con formato inválido para key={}, re-consultando LeyData", key);
                        return Mono.empty();
                    }
                })
                .switchIfEmpty(fetchFromLeydata(subjectId, purposeId, key));
    }

    private Mono<ConsentCheckResponse> fetchFromLeydata(String subjectId, UUID purposeId, String key) {
        return leydataClient.get()
                .uri("/api/agreements/active?dataSubjectId={s}&templateId={p}", subjectId, purposeId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, resp -> Mono.empty())
                .bodyToMono(AgreementBackendResponse.class)
                .flatMap(agreement -> {
                    ConsentCheckResponse response = buildEnrichedResponse(subjectId, purposeId, agreement);
                    return cacheJson(key, response).thenReturn(response);
                })
                // LeyData respondió 404 o vacío → titular nunca ha otorgado consentimiento
                .switchIfEmpty(Mono.just(pendingResponse(subjectId, purposeId)));
    }

    private ConsentCheckResponse buildEnrichedResponse(String subjectId, UUID purposeId,
                                                        AgreementBackendResponse agreement) {
        String status = switch (agreement.status() != null ? agreement.status() : "") {
            case "ACTIVE"   -> "ALLOWED";
            case "REVOKED"  -> "REVOKED";
            case "EXPIRED"  -> "DENIED";
            default         -> "PENDING";
        };

        // Buscar el purpose específico aceptado dentro del acuerdo
        AgreementPurposeBackendResponse matched = agreement.purposes() == null ? null :
                agreement.purposes().stream()
                        .filter(p -> purposeId.equals(p.purposeId()) && Boolean.TRUE.equals(p.accepted()))
                        .findFirst()
                        .orElse(null);

        String legalBasisCode = matched != null ? matched.legalBasisCode() : null;

        // validUntil: expiresAt del purpose específico; fallback al expiration del acuerdo
        LocalDateTime validUntil = (matched != null && matched.expiresAt() != null)
                ? matched.expiresAt()
                : agreement.expiration();

        return new ConsentCheckResponse(subjectId, purposeId, status, legalBasisCode, validUntil);
    }

    // ── CAPTURE ───────────────────────────────────────────────────────────────

    public Mono<ConsentStatusResponse> capture(CaptureConsentRequest request, String realIp) {
        Map<String, Object> leydataBody = Map.of(
                "subjectIdentifier", request.subjectId(),
                "templateId",    request.templateId(),
                "documentId",    request.documentId(),
                "purposes",      request.purposes(),
                "metadata", Map.of(
                        "captureChannel", "ORCHESTRATOR",
                        "ipAddress",      realIp != null ? realIp : "unknown",
                        "authProvider",   "external-jwt"
                )
        );

        return leydataClient.post()
                .uri("/api/agreements")
                .header("X-Internal-Real-IP", realIp != null ? realIp : "unknown")
                .bodyValue(leydataBody)
                .retrieve()
                .bodyToMono(AgreementBackendResponse.class)
                .flatMap(agreement -> {
                    UUID agreementId = agreement.id();

                    // Pre-calentar Redis por cada purpose aceptado.
                    // legalBasisCode y validUntil se escriben con los valores reales del acuerdo creado.
                    List<Mono<Boolean>> writes = request.purposes().stream()
                            .filter(CaptureConsentRequest.PurposeDecision::accepted)
                            .map(p -> {
                                AgreementPurposeBackendResponse matched = agreement.purposes() == null ? null :
                                        agreement.purposes().stream()
                                                .filter(ap -> p.purposeId().equals(ap.purposeId()))
                                                .findFirst().orElse(null);

                                LocalDateTime validUntil = (matched != null && matched.expiresAt() != null)
                                        ? matched.expiresAt()
                                        : agreement.expiration();
                                String legalBasisCode = matched != null ? matched.legalBasisCode() : null;

                                ConsentCheckResponse cached = new ConsentCheckResponse(
                                        request.subjectId(), p.purposeId(), "ALLOWED",
                                        legalBasisCode, validUntil);

                                return cacheJson(cacheKey(request.subjectId(), p.purposeId()), cached);
                            })
                            .toList();

                    return Mono.when(writes)
                            .thenReturn(new ConsentStatusResponse(request.subjectId(), agreementId, "ALLOWED"));
                });
    }

    // ── REVOKE ────────────────────────────────────────────────────────────────

    public Mono<ConsentStatusResponse> revoke(RevokeConsentRequest request, String realIp) {
        return leydataClient.patch()
                .uri("/api/agreements/{id}/revoke", request.agreementId())
                .header("X-Internal-Real-IP", realIp != null ? realIp : "unknown")
                .bodyValue(Map.of("subjectId", request.subjectId()))
                .retrieve()
                .bodyToMono(AgreementBackendResponse.class)
                .flatMap(agreement -> {
                    // Escribir REVOKED en Redis por cada purpose del acuerdo (no borrar — evita split-brain)
                    List<Mono<Boolean>> writes = agreement.purposes() == null ? List.of() :
                            agreement.purposes().stream()
                                    .filter(p -> p.purposeId() != null)
                                    .map(p -> {
                                        ConsentCheckResponse revoked = new ConsentCheckResponse(
                                                request.subjectId(), p.purposeId(), "REVOKED",
                                                p.legalBasisCode(), p.expiresAt());
                                        return cacheJson(
                                                cacheKey(request.subjectId(), p.purposeId()), revoked);
                                    })
                                    .toList();

                    return Mono.when(writes)
                            .thenReturn(new ConsentStatusResponse(
                                    request.subjectId(), request.agreementId(), "REVOKED"));
                });
    }

    // ── UTIL ──────────────────────────────────────────────────────────────────

    private Mono<Boolean> cacheJson(String key, ConsentCheckResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            return redis.opsForValue().set(key, json, Duration.ofSeconds(cacheProps.cacheTtlSeconds()));
        } catch (Exception e) {
            log.warn("No se pudo serializar ConsentCheckResponse para key={}: {}", key, e.getMessage());
            return Mono.just(false);
        }
    }

    private ConsentCheckResponse pendingResponse(String subjectId, UUID purposeId) {
        return new ConsentCheckResponse(subjectId, purposeId, "PENDING", null, null);
    }

    private String cacheKey(String subjectId, UUID id) {
        return "consent:" + subjectId + ":" + id;
    }
}
