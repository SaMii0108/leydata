package com.leydata.orchestrator.consent;

import com.leydata.orchestrator.consent.dto.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/consent")
@RequiredArgsConstructor
public class ConsentController {

    private final ConsentService consentService;
    private final MeterRegistry meterRegistry;

    @GetMapping("/check")
    public Mono<ResponseEntity<ConsentCheckResponse>> check(
            @RequestParam String subjectId,
            @RequestParam UUID purposeId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        return consentService.check(subjectId, purposeId)
                .doOnSuccess(r -> sample.stop(meterRegistry.timer("consent.check", "status", "ok")))
                .doOnError(e -> sample.stop(meterRegistry.timer("consent.check", "status", "error")))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/capture")
    public Mono<ResponseEntity<ConsentStatusResponse>> capture(
            @RequestBody CaptureConsentRequest request,
            @AuthenticationPrincipal Jwt jwt,
            ServerHttpRequest httpRequest) {
        String realIp = extractRealIp(httpRequest);
        UUID domainId = extractDomainId(jwt);
        return consentService.capture(domainId, request, realIp)
                .map(r -> ResponseEntity.status(201).body(r));
    }

    @PostMapping("/revoke")
    public Mono<ResponseEntity<ConsentStatusResponse>> revoke(
            @RequestBody RevokeConsentRequest request,
            ServerHttpRequest httpRequest) {
        String realIp = extractRealIp(httpRequest);
        return consentService.revoke(request, realIp)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/template-content")
    public Mono<ResponseEntity<TemplateContentResponse>> templateContent(
            @RequestParam String templateKey,
            @AuthenticationPrincipal Jwt jwt) {
        UUID domainId = extractDomainId(jwt);
        return consentService.getTemplateContent(domainId, templateKey)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/subject/{subjectId}")
    public Mono<ResponseEntity<List<SubjectSummaryBackendResponse>>> subjectSummary(
            @PathVariable String subjectId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID domainId = extractDomainId(jwt);
        return consentService.getSubjectSummary(domainId, subjectId)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/revoke-purpose")
    public Mono<ResponseEntity<ConsentStatusResponse>> revokePurpose(
            @RequestBody RevokePurposeRequest request,
            @AuthenticationPrincipal Jwt jwt,
            ServerHttpRequest httpRequest) {
        String realIp = extractRealIp(httpRequest);
        UUID domainId = extractDomainId(jwt);
        return consentService.revokePurpose(domainId, request, realIp)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/pending-deletions")
    public Mono<ResponseEntity<List<PendingDeletionBackendItem>>> pendingDeletions(
            @AuthenticationPrincipal Jwt jwt) {
        UUID domainId = extractDomainId(jwt);
        return consentService.getPendingDeletions(domainId)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/confirm-deletion")
    public Mono<ResponseEntity<Void>> confirmDeletion(
            @RequestBody OrchestratorConfirmDeletionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID domainId = extractDomainId(jwt);
        return consentService.confirmDeletion(domainId, request)
                .thenReturn(ResponseEntity.<Void>status(HttpStatus.NO_CONTENT).build());
    }

    /**
     * Claim hardcodeado por protocol mapper en Keycloak, por cada client M2M de sistema cliente
     * (CRM, ERP). Identifica el dominio LeyData al que ese sistema está autorizado a consentir.
     */
    private UUID extractDomainId(Jwt jwt) {
        String raw = jwt.getClaimAsString("leydata_domain");
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException(
                    "El JWT del sistema cliente no incluye el claim 'leydata_domain'. " +
                    "Verificar el protocol mapper del client en Keycloak.");
        }
        return UUID.fromString(raw);
    }

    private String extractRealIp(ServerHttpRequest request) {
        String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddress() != null
                ? request.getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }
}
