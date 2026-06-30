package com.leydata.orchestrator.consent;

import com.leydata.orchestrator.consent.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/consent")
@RequiredArgsConstructor
public class ConsentController {

    private final ConsentService consentService;

    @GetMapping("/check")
    public Mono<ResponseEntity<ConsentCheckResponse>> check(
            @RequestParam String subjectId,
            @RequestParam UUID purposeId) {
        return consentService.check(subjectId, purposeId)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/capture")
    public Mono<ResponseEntity<ConsentStatusResponse>> capture(
            @RequestBody CaptureConsentRequest request,
            ServerHttpRequest httpRequest) {
        String realIp = extractRealIp(httpRequest);
        return consentService.capture(request, realIp)
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
