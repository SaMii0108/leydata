package com.leydata.orchestrator.consent;

import com.leydata.orchestrator.config.ConsentCacheProperties;
import com.leydata.orchestrator.consent.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
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

    public Mono<ConsentStatusResponse> capture(UUID domainId, CaptureConsentRequest request, String realIp) {
        return resolveTemplate(domainId, request.templateKey())
                .flatMap(resolution -> doCapture(request, resolution, realIp));
    }

    private Mono<TemplateResolutionResponse> resolveTemplate(UUID domainId, String templateKey) {
        return leydataClient.get()
                .uri("/api/templates/resolve?domainId={d}&templateKey={k}", domainId, templateKey)
                .retrieve()
                .bodyToMono(TemplateResolutionResponse.class);
    }

    private Mono<ConsentStatusResponse> doCapture(CaptureConsentRequest request,
                                                   TemplateResolutionResponse resolution, String realIp) {
        UUID documentId = request.documentId() != null ? request.documentId() : resolution.documentId();
        if (documentId == null) {
            return Mono.error(new IllegalStateException(
                    "El template " + request.templateKey() + " no tiene un documento publicado asociado"));
        }

        Map<String, Object> leydataBody = Map.of(
                "subjectIdentifier", request.subjectId(),
                "templateId",    resolution.templateId(),
                "documentId",    documentId,
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

    // ── TEMPLATE CONTENT (Caso 0 — textos legales para el modal) ─────────────

    public Mono<TemplateContentResponse> getTemplateContent(UUID domainId, String templateKey) {
        return leydataClient.get()
                .uri("/api/templates/resolve?domainId={d}&templateKey={k}", domainId, templateKey)
                .retrieve()
                .bodyToMono(TemplateResolutionResponse.class)
                .flatMap(resolution -> leydataClient.get()
                        .uri("/api/templates/{id}/purposes", resolution.templateId())
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                        .map(purposesList -> {
                            List<TemplateContentResponse.PurposeItem> items = purposesList.stream()
                                    .map(p -> new TemplateContentResponse.PurposeItem(
                                            p.get("purposeId") != null ? UUID.fromString((String) p.get("purposeId")) : null,
                                            (String) p.get("purposeCode"),
                                            (String) p.get("purposeName"),
                                            (String) p.get("purposeDescription"),
                                            (String) p.get("purposeShortDescription"),
                                            Boolean.TRUE.equals(p.get("required")),
                                            Boolean.TRUE.equals(p.get("revocable")),
                                            (String) p.get("legalBasisCode")
                                    ))
                                    .toList();
                            return new TemplateContentResponse(
                                    resolution.templateId(),
                                    resolution.domainId(),
                                    resolution.templateKey(),
                                    resolution.version(),
                                    null, null, null,
                                    resolution.documentId(),
                                    items
                            );
                        })
                );
    }

    // ── LIFECYCLE CHECK (CHECK con estado de ciclo de vida) ───────────────────

    public Mono<ConsentCheckResponse> checkWithLifecycle(UUID domainId, String subjectId, UUID purposeId,
                                                          String templateKey) {
        String cacheKey = cacheKey(subjectId, purposeId);
        return redis.opsForValue().get(cacheKey)
                .flatMap(json -> {
                    try {
                        return Mono.just(objectMapper.readValue(json, ConsentCheckResponse.class));
                    } catch (Exception e) {
                        return Mono.empty();
                    }
                })
                .switchIfEmpty(fetchLifecycleFromBackend(domainId, subjectId, purposeId, templateKey, cacheKey));
    }

    private Mono<ConsentCheckResponse> fetchLifecycleFromBackend(UUID domainId, String subjectId, UUID purposeId,
                                                                   String templateKey, String cacheKey) {
        return leydataClient.get()
                .uri("/api/agreements/lifecycle-check?subjectIdentifier={s}&domainId={d}&templateKey={k}",
                        subjectId, domainId, templateKey)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, resp -> Mono.empty())
                .bodyToMono(LifecycleCheckBackendResponse.class)
                .flatMap(lifecycle -> {
                    String status = switch (lifecycle.status() != null ? lifecycle.status() : "") {
                        case "ALLOWED"            -> "ALLOWED";
                        case "EXPIRED"            -> "EXPIRED";
                        case "REQUIRES_RECONSENT" -> "REQUIRES_RECONSENT";
                        default                   -> "PENDING";
                    };
                    ConsentCheckResponse response = new ConsentCheckResponse(
                            subjectId, purposeId, status, null, lifecycle.earliestExpiresAt());
                    return cacheJson(cacheKey, response).thenReturn(response);
                })
                .switchIfEmpty(Mono.just(pendingResponse(subjectId, purposeId)));
    }

    // ── SUBJECT SUMMARY (portal del titular) ─────────────────────────────────

    public Mono<List<SubjectSummaryBackendResponse>> getSubjectSummary(UUID domainId, String subjectId) {
        return leydataClient.get()
                .uri("/api/agreements/subject-summary?subjectIdentifier={s}&domainId={d}", subjectId, domainId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<SubjectSummaryBackendResponse>>() {});
    }

    // ── REVOKE PURPOSE (re-consentimiento granular) ───────────────────────────

    public Mono<ConsentStatusResponse> revokePurpose(UUID domainId, RevokePurposeRequest request, String realIp) {
        // 1. Obtener estado actual de todas las purposes del subject en ese template
        return leydataClient.get()
                .uri("/api/agreements/subject-summary?subjectIdentifier={s}&domainId={d}",
                        request.subjectId(), domainId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<SubjectSummaryBackendResponse>>() {})
                .flatMap(summaries -> {
                    // Encontrar el acuerdo que contiene la purpose a revocar
                    SubjectSummaryBackendResponse targetAgreement = summaries.stream()
                            .filter(s -> s.templateKey() != null &&
                                    s.templateKey().equalsIgnoreCase(request.templateKey()) &&
                                    s.purposes().stream()
                                            .anyMatch(p -> request.purposeId().equals(p.purposeId())))
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "No se encontró acuerdo activo con purposeId=" + request.purposeId()
                                    + " para templateKey=" + request.templateKey()));

                    // 2. Verificar que la purpose es revocable
                    SubjectSummaryBackendResponse.PurposeItem targetPurpose = targetAgreement.purposes().stream()
                            .filter(p -> request.purposeId().equals(p.purposeId()))
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Purpose no encontrada: " + request.purposeId()));

                    if (!targetPurpose.revocable()) {
                        throw new IllegalArgumentException(
                                "La purpose " + targetPurpose.purposeCode() + " no es revocable");
                    }
                    if (targetPurpose.required()) {
                        throw new IllegalArgumentException(
                                "La purpose " + targetPurpose.purposeCode() + " es requerida y no puede revocarse");
                    }

                    // 3. Reconstruir el body con la purpose objetivo en accepted=false
                    List<Map<String, Object>> newPurposes = targetAgreement.purposes().stream()
                            .map(p -> {
                                boolean accepted = !request.purposeId().equals(p.purposeId()) && p.accepted();
                                Map<String, Object> purposeMap = new java.util.LinkedHashMap<>();
                                purposeMap.put("purposeId", p.purposeId().toString());
                                purposeMap.put("accepted", accepted);
                                return purposeMap;
                            })
                            .collect(java.util.stream.Collectors.toList());

                    Map<String, Object> body = Map.of(
                            "subjectIdentifier", request.subjectId(),
                            "templateId",        targetAgreement.templateId().toString(),
                            "documentId",        targetAgreement.documentId().toString(),
                            "purposes",          newPurposes,
                            "metadata", Map.of(
                                    "captureChannel", "ORCHESTRATOR_PORTAL",
                                    "authProvider",   "external-jwt"
                            )
                    );

                    // 4. Crear nuevo acuerdo (el backend archiva el anterior automáticamente)
                    return leydataClient.post()
                            .uri("/api/agreements")
                            .header("X-Internal-Real-IP", realIp != null ? realIp : "unknown")
                            .bodyValue(body)
                            .retrieve()
                            .bodyToMono(AgreementBackendResponse.class)
                            .flatMap(newAgreement -> {
                                // 5. Actualizar Redis: solo la purpose revocada cambia a REVOKED
                                ConsentCheckResponse revoked = new ConsentCheckResponse(
                                        request.subjectId(), request.purposeId(), "REVOKED", null, null);
                                return cacheJson(cacheKey(request.subjectId(), request.purposeId()), revoked)
                                        .thenReturn(new ConsentStatusResponse(
                                                request.subjectId(), newAgreement.id(), "REVOKED"));
                            });
                });
    }

    // ── PENDING DELETIONS ─────────────────────────────────────────────────────

    public Mono<List<PendingDeletionBackendItem>> getPendingDeletions(UUID domainId) {
        return leydataClient.get()
                .uri("/api/agreements/pending-deletions?domainId={d}", domainId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<PendingDeletionBackendItem>>() {});
    }

    // ── CONFIRM DELETION ──────────────────────────────────────────────────────

    public Mono<Void> confirmDeletion(UUID domainId, OrchestratorConfirmDeletionRequest request) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("subjectIdentifier", request.subjectId());
        body.put("purposeId", request.purposeId().toString());
        if (request.deletedAt() != null) body.put("deletedAt", request.deletedAt().toString());

        return leydataClient.post()
                .uri("/api/agreements/confirm-deletion")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Void.class)
                .then(
                    // Limpiar la key de Redis: marcar como EXPIRED
                    cacheJson(cacheKey(request.subjectId(), request.purposeId()),
                            new ConsentCheckResponse(request.subjectId(), request.purposeId(),
                                    "EXPIRED", null, null))
                            .then()
                );
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
