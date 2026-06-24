package com.leydata.backend.purposedatacategory.web;

import com.leydata.backend.purposedatacategory.application.dto.DataRetentionPolicyRequest;
import com.leydata.backend.purposedatacategory.application.dto.PurposeDataCategoryRequest;
import com.leydata.backend.purposedatacategory.application.dto.PurposeDataCategoryResponse;
import com.leydata.backend.purposedatacategory.application.service.PurposeDataCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/purposes/{purposeId}/data-categories")
@RequiredArgsConstructor
public class PurposeDataCategoryController {

    private final PurposeDataCategoryService service;

    // GET /api/purposes/{purposeId}/data-categories
    // Devuelve las categorías con retentionLocked=true si hay doc publicado
    @GetMapping
    @PreAuthorize("hasAnyRole('DPO','ADMIN','JEFE_DOMINIO')")
    public List<PurposeDataCategoryResponse> listByPurpose(@PathVariable UUID purposeId) {
        return service.listByPurpose(purposeId);
    }

    // GET /api/purposes/{purposeId}/data-categories/{id}
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('DPO','ADMIN','JEFE_DOMINIO')")
    public PurposeDataCategoryResponse getById(@PathVariable UUID purposeId,
                                               @PathVariable UUID id) {
        return service.getById(id);
    }

    // POST /api/purposes/{purposeId}/data-categories
    // Vincula una categoría + define su política de retención en un solo paso
    @PostMapping
    @PreAuthorize("hasAnyRole('DPO','ADMIN')")
    public ResponseEntity<PurposeDataCategoryResponse> link(@PathVariable UUID purposeId,
                                                            @Valid @RequestBody PurposeDataCategoryRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.link(purposeId, req));
    }

    // PUT /api/purposes/{purposeId}/data-categories/{id}/retention
    // Actualizar política de retención — BLOQUEADO si hay documento PUBLISHED
    @PutMapping("/{id}/retention")
    @PreAuthorize("hasAnyRole('DPO','ADMIN')")
    public PurposeDataCategoryResponse updateRetention(@PathVariable UUID purposeId,
                                                       @PathVariable UUID id,
                                                       @Valid @RequestBody DataRetentionPolicyRequest req) {
        return service.updateRetention(id, req);
    }

    // DELETE /api/purposes/{purposeId}/data-categories/{id}
    // Desvincula la categoría — BLOQUEADO si hay documento PUBLISHED
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('DPO','ADMIN')")
    public ResponseEntity<Void> unlink(@PathVariable UUID purposeId,
                                       @PathVariable UUID id) {
        service.unlink(id);
        return ResponseEntity.noContent().build();
    }
}
