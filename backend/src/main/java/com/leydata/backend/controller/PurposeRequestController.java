package com.leydata.backend.controller;

import com.leydata.backend.dto.PurposeRequestDto;
import com.leydata.backend.dto.PurposeRequestSummaryDto;
import com.leydata.backend.dto.ReviewRequestDto;
import com.leydata.backend.service.PurposeRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<?> createPurposeRequest(@RequestBody PurposeRequestDto request) {
        try {
            PurposeRequestSummaryDto created = purposeRequestService.createPurposeRequest(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "status", "success",
                    "message", "Solicitud enviada al DPO correctamente",
                    "request", created));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/purpose-requests/my
    @PreAuthorize("hasRole('JEFE_DOMINIO')")
    @GetMapping("/my")
    public ResponseEntity<?> getMyRequests() {
        try {
            List<PurposeRequestSummaryDto> requests = purposeRequestService.getMyRequests();
            return ResponseEntity.ok(Map.of("status", "success", "requests", requests));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/purpose-requests/pending
    @PreAuthorize("hasRole('DPO')")
    @GetMapping("/pending")
    public ResponseEntity<?> getPendingRequests() {
        try {
            List<PurposeRequestSummaryDto> requests = purposeRequestService.getPendingRequests();
            return ResponseEntity.ok(Map.of("status", "success", "requests", requests));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/purpose-requests
    @PreAuthorize("hasRole('DPO')")
    @GetMapping
    public ResponseEntity<?> getAllRequests() {
        try {
            List<PurposeRequestSummaryDto> requests = purposeRequestService.getAllRequests();
            return ResponseEntity.ok(Map.of("status", "success", "requests", requests));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    // PATCH /api/purpose-requests/{id}/review
    @PreAuthorize("hasRole('DPO')")
    @PatchMapping("/{requestId}/review")
    public ResponseEntity<?> reviewRequest(@PathVariable UUID requestId,
            @RequestBody ReviewRequestDto reviewRequest) {
        try {
            PurposeRequestSummaryDto reviewed = purposeRequestService
                    .reviewRequest(requestId, reviewRequest);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Solicitud revisada correctamente",
                    "request", reviewed));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }
}