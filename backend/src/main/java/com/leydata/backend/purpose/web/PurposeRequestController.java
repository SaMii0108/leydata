package com.leydata.backend.purpose.web;

import com.leydata.backend.purpose.application.dto.PurposeRequestDto;
import com.leydata.backend.purpose.application.dto.PurposeRequestSummaryDto;
import com.leydata.backend.purpose.application.dto.ReviewRequestDto;
import com.leydata.backend.purpose.application.service.PurposeRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/purpose-requests")
@RequiredArgsConstructor
public class PurposeRequestController {

    private final PurposeRequestService purposeRequestService;

    // POST /api/purpose-requests
    @PreAuthorize("hasRole('JEFE_DOMINIO')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createPurposeRequest(@RequestBody PurposeRequestDto request) {
        PurposeRequestSummaryDto created = purposeRequestService.createPurposeRequest(request);
        return Map.of(
                "status", "success",
                "message", "Solicitud enviada al DPO correctamente",
                "request", created);
    }

    // GET /api/purpose-requests/my
    @PreAuthorize("hasRole('JEFE_DOMINIO')")
    @GetMapping("/my")
    public Map<String, Object> getMyRequests() {
        List<PurposeRequestSummaryDto> requests = purposeRequestService.getMyRequests();
        return Map.of("status", "success", "requests", requests);
    }

    // GET /api/purpose-requests/pending
    @PreAuthorize("hasAnyRole('DPO', 'ADMIN')")
    @GetMapping("/pending")
    public Map<String, Object> getPendingRequests() {
        List<PurposeRequestSummaryDto> requests = purposeRequestService.getPendingRequests();
        return Map.of("status", "success", "requests", requests);
    }

    // GET /api/purpose-requests
    @PreAuthorize("hasAnyRole('DPO', 'ADMIN')")
    @GetMapping
    public Map<String, Object> getAllRequests() {
        List<PurposeRequestSummaryDto> requests = purposeRequestService.getAllRequests();
        return Map.of("status", "success", "requests", requests);
    }

    // PATCH /api/purpose-requests/{requestId}/review
    @PreAuthorize("hasRole('DPO')")
    @PatchMapping("/{requestId}/review")
    public Map<String, Object> reviewRequest(@PathVariable UUID requestId,
            @RequestBody ReviewRequestDto reviewRequest) {
        PurposeRequestSummaryDto reviewed = purposeRequestService.reviewRequest(requestId, reviewRequest);
        return Map.of(
                "status", "success",
                "message", "Solicitud revisada correctamente",
                "request", reviewed);
    }
}
