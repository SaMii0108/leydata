package com.leydata.backend.privacydoc.web;

import com.leydata.backend.privacydoc.application.dto.*;
import com.leydata.backend.privacydoc.application.service.PrivacyDocumentService;
import com.leydata.backend.privacydoc.domain.enums.DocumentCategory;
import com.leydata.backend.privacydoc.domain.enums.DocumentStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/privacy-documents")
@RequiredArgsConstructor
@Tag(name = "Privacy Documents", description = "Gestión de Documentos de Privacidad — Ley 21.719")
public class PrivacyDocumentController {

    private final PrivacyDocumentService service;

    // ── CRUD ─────────────────────────────────────────────────────────────────────

    @PreAuthorize("hasRole('DPO')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear documento en DRAFT [DPO]")
    public PrivacyDocumentResponse create(@Valid @RequestBody CreateDocumentRequest req) {
        return service.create(req);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener documento por ID")
    public PrivacyDocumentResponse getById(@PathVariable UUID id) {
        return service.getById(id);
    }

    @GetMapping
    @Operation(summary = "Listar documentos con filtros opcionales")
    public List<PrivacyDocumentResponse> list(
            @RequestParam(required = false) DocumentCategory category,
            @RequestParam(required = false) DocumentStatus status) {
        return service.list(category, status);
    }

    @PreAuthorize("hasRole('DPO')")
    @PatchMapping("/{id}")
    @Operation(summary = "Editar documento en DRAFT [DPO]")
    public PrivacyDocumentResponse update(
            @PathVariable UUID id,
            @RequestBody UpdateDocumentRequest req) {
        return service.update(id, req);
    }

    @PreAuthorize("hasRole('DPO')")
    @PostMapping("/{id}/deactivate")
    @Operation(summary = "Desactivar documento — no lo borra, queda para auditoría [DPO]")
    public PrivacyDocumentResponse deactivate(@PathVariable UUID id) {
        return service.deactivate(id);
    }

    @PreAuthorize("hasRole('DPO')")
    @PostMapping("/{id}/new-version")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear nueva versión del documento en la misma familia [DPO]")
    public PrivacyDocumentResponse newVersion(@PathVariable UUID id) {
        return service.newVersion(id);
    }

    @GetMapping("/family/{familyId}")
    @Operation(summary = "Listar todas las versiones activas de una familia de documentos")
    public List<PrivacyDocumentResponse> getByFamily(@PathVariable UUID familyId) {
        return service.getByFamily(familyId);
    }

    // ── PROPÓSITOS ───────────────────────────────────────────────────────────────

    @PreAuthorize("hasRole('DPO')")
    @PostMapping("/{id}/purposes/{purposeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Vincular propósito (solo DRAFT) [DPO]")
    public void addPurpose(@PathVariable UUID id, @PathVariable UUID purposeId) {
        service.addPurpose(id, purposeId);
    }

    @PreAuthorize("hasRole('DPO')")
    @DeleteMapping("/{id}/purposes/{purposeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Desvincular propósito (solo DRAFT) [DPO]")
    public void removePurpose(@PathVariable UUID id, @PathVariable UUID purposeId) {
        service.removePurpose(id, purposeId);
    }

    // ── WORKFLOW ─────────────────────────────────────────────────────────────────

    @PreAuthorize("hasRole('DPO')")
    @PostMapping("/{id}/submit")
    @Operation(summary = "DRAFT → IN_REVIEW [DPO]")
    public PrivacyDocumentResponse submit(@PathVariable UUID id) {
        return service.submit(id);
    }

    @PreAuthorize("hasRole('DPO')")
    @PostMapping("/{id}/resubmit")
    @Operation(summary = "REJECTED → IN_REVIEW [DPO]")
    public PrivacyDocumentResponse resubmit(@PathVariable UUID id) {
        return service.resubmit(id);
    }

    @PreAuthorize("hasRole('DPO')")
    @PostMapping("/{id}/approve")
    @Operation(summary = "IN_REVIEW → APPROVED [DPO]")
    public PrivacyDocumentResponse approve(@PathVariable UUID id) {
        return service.approve(id);
    }

    @PreAuthorize("hasRole('DPO')")
    @PostMapping("/{id}/reject")
    @Operation(summary = "IN_REVIEW → REJECTED [DPO] — motivo obligatorio")
    public PrivacyDocumentResponse reject(
            @PathVariable UUID id,
            @Valid @RequestBody RejectDocumentRequest req) {
        return service.reject(id, req);
    }

    @PreAuthorize("hasRole('DPO')")
    @PostMapping("/{id}/publish")
    @Operation(summary = "APPROVED → PUBLISHED [DPO] — genera PDF + SHA-256")
    public PrivacyDocumentResponse publish(@PathVariable UUID id) {
        return service.publish(id);
    }

    @PreAuthorize("hasRole('DPO')")
    @PostMapping("/{id}/archive")
    @Operation(summary = "PUBLISHED → ARCHIVED [DPO]")
    public PrivacyDocumentResponse archive(@PathVariable UUID id) {
        return service.archive(id);
    }

    // ── UTILIDADES ───────────────────────────────────────────────────────────────

    /**
     * Descarga el PDF binario del documento.
     * Solo disponible cuando status = PUBLISHED o ARCHIVED (tiene pdfContent).
     */
    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Descargar PDF del documento publicado")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable UUID id) {
        byte[] pdfBytes = service.downloadPdf(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"privacy-document-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdfBytes.length)
                .body(pdfBytes);
    }

    @GetMapping("/{id}/verify")
    @Operation(summary = "Verificar integridad SHA-256 del PDF almacenado en BD")
    public VerifyResponse verify(@PathVariable UUID id) {
        return service.verify(id);
    }

    @GetMapping("/active")
    @Operation(summary = "Documento PUBLISHED activo por categoría")
    public PrivacyDocumentResponse getActive(
            @RequestParam DocumentCategory category) {
        return service.getActive(category);
    }
}
