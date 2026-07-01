package com.leydata.backend.agreement.web;

import com.leydata.backend.agreement.application.dto.*;
import com.leydata.backend.agreement.application.service.AgreementService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/agreements")
@RequiredArgsConstructor
@Tag(name = "Agreements", description = "Acuerdos de consentimiento")
public class AgreementController {

    private final AgreementService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear agreement con el detalle de purposes aceptadas/rechazadas")
    public AgreementResponse create(@Valid @RequestBody CreateAgreementRequest req, HttpServletRequest request) {
        return service.create(req, request.getRemoteAddr(), request.getHeader("User-Agent"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener agreement por ID con su detalle de purposes")
    public AgreementResponse getById(@PathVariable UUID id) {
        return service.getById(id);
    }

    @GetMapping("/active")
    @Operation(summary = "Consultar si existe un agreement ACTIVE para (dataSubjectId, templateId) — para que el orquestador decida si pedir consentimiento")
    public ResponseEntity<AgreementResponse> getActive(
            @RequestParam UUID dataSubjectId,
            @RequestParam UUID templateId) {
        return service.getActive(dataSubjectId, templateId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    @Operation(summary = "Listar agreements con filtros opcionales")
    public List<AgreementResponse> list(
            @RequestParam(required = false) UUID dataSubjectId,
            @RequestParam(required = false) UUID templateId,
            @RequestParam(required = false) String status) {
        return service.list(dataSubjectId, templateId, status);
    }

    @PatchMapping("/{id}/revoke")
    @Operation(summary = "Revocar un agreement — cambia estado a REVOKED y sus purposes asociados",
               description = "Llamado por el Orquestador. La IP real del titular viene en X-Internal-Real-IP si la petición viene de la red interna.")
    public AgreementResponse revoke(
            @PathVariable UUID id,
            @RequestBody RevokeAgreementRequest req,
            HttpServletRequest request) {
        String realIp = resolveRealIp(request);
        return service.revoke(id, req.subjectId(), realIp);
    }

    @GetMapping("/lifecycle-check")
    @Operation(summary = "Estado del ciclo de vida del consentimiento para un titular y template " +
            "[uso interno B2B — llamado por el Orquestador]",
               description = "Devuelve ALLOWED | EXPIRED | REQUIRES_RECONSENT | PENDING")
    public ConsentLifecycleResponse lifecycleCheck(
            @RequestParam String subjectIdentifier,
            @RequestParam UUID domainId,
            @RequestParam String templateKey) {
        return service.getLifecycleStatus(subjectIdentifier, domainId, templateKey);
    }

    @GetMapping("/subject-summary")
    @Operation(summary = "Estado de todas las purposes de un titular en un dominio [uso interno B2B]",
               description = "Devuelve los switches para el portal del titular")
    public List<SubjectSummaryResponse> subjectSummary(
            @RequestParam String subjectIdentifier,
            @RequestParam UUID domainId) {
        return service.getSubjectSummary(subjectIdentifier, domainId);
    }

    @GetMapping("/pending-deletions")
    @Operation(summary = "Purposes vencidas pendientes de eliminación de datos en el CRM [uso interno B2B]",
               description = "El CRM consulta esto periódicamente para cumplir el deber de supresión de la Ley 21.719")
    public List<PendingDeletionItem> pendingDeletions(@RequestParam UUID domainId) {
        return service.getPendingDeletions(domainId);
    }

    @PostMapping("/confirm-deletion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "El CRM confirma que eliminó los datos de una purpose vencida [uso interno B2B]",
               description = "Registra evidencia de eliminación en el log de auditoría para fiscalizaciones")
    public void confirmDeletion(@Valid @RequestBody ConfirmDeletionRequest req) {
        service.confirmDeletion(req);
    }

    private String resolveRealIp(HttpServletRequest request) {
        String internalIp = request.getHeader("X-Internal-Real-IP");
        if (internalIp != null && !internalIp.isBlank()) {
            return internalIp;
        }
        return request.getRemoteAddr();
    }

    public record RevokeAgreementRequest(String subjectId) {}
}
