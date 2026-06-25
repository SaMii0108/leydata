package com.leydata.backend.template.web;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.leydata.backend.template.application.dto.AddTemplatePurposeRequest;
import com.leydata.backend.template.application.dto.CreateTemplateRequest;
import com.leydata.backend.template.application.dto.TemplatePurposeResponse;
import com.leydata.backend.template.application.dto.TemplateResponse;
import com.leydata.backend.template.application.dto.TemplateVerifyResponse;
import com.leydata.backend.template.application.dto.UpdateTemplatePurposeRequest;
import com.leydata.backend.template.application.service.TemplateService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DPO')")
@Tag(name = "Templates", description = "Gestión de plantillas de consentimiento [DPO]")
public class TemplateController {

    private final TemplateService service;

    // ── CRUD ─────────────────────────────────────────────────────────────────────

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear template en DRAFT [DPO]")
    public TemplateResponse create(@Valid @RequestBody CreateTemplateRequest req) {
        return service.create(req);
    }

    @PostMapping("/{id}/new-version")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear nueva versión del template (mismo TEMPLATE_KEY) [DPO]")
    public TemplateResponse newVersion(@PathVariable UUID id) {
        return service.newVersion(id);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener template por ID [DPO]")
    public TemplateResponse getById(@PathVariable UUID id) {
        return service.getById(id);
    }

    @GetMapping
    @Operation(summary = "Listar templates con filtros opcionales [DPO]")
    public List<TemplateResponse> list(
            @RequestParam(required = false) String templateKey,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) UUID createdBy,
            @RequestParam(required = false) UUID approvedBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdAfter,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdBefore) {
        return service.list(templateKey, isActive, createdBy, approvedBy, createdAfter, createdBefore);
    }

    @GetMapping("/family/{templateKey}")
    @Operation(summary = "Historial de versiones de un TEMPLATE_KEY [DPO]")
    public List<TemplateResponse> getHistory(@PathVariable String templateKey) {
        return service.getHistory(templateKey);
    }

    @GetMapping("/active/{templateKey}")
    @Operation(summary = "Obtener la versión activa de un TEMPLATE_KEY [DPO]")
    public TemplateResponse getActive(@PathVariable String templateKey) {
        return service.getActive(templateKey);
    }

    // ── INTEGRIDAD ───────────────────────────────────────────────────────────────

    @GetMapping("/{id}/verify")
    @Operation(summary = "Verificar integridad SHA-256 del template activado [DPO]")
    public TemplateVerifyResponse verify(@PathVariable UUID id) {
        return service.verify(id);
    }

    // ── WORKFLOW ─────────────────────────────────────────────────────────────────

    @PostMapping("/{id}/approve")
    @Operation(summary = "Aprobar template [DPO]")
    public TemplateResponse approve(@PathVariable UUID id) {
        return service.approve(id);
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Activar template — desactiva la versión anterior del mismo TEMPLATE_KEY [DPO]")
    public TemplateResponse activate(@PathVariable UUID id) {
        return service.activate(id);
    }

    // ── PURPOSES ─────────────────────────────────────────────────────────────────

    @PostMapping("/{id}/purposes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Vincular purpose al template (solo DRAFT) [DPO]")
    public void addPurpose(@PathVariable UUID id, @Valid @RequestBody AddTemplatePurposeRequest req) {
        service.addPurpose(id, req);
    }

    @DeleteMapping("/{id}/purposes/{purposeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Desvincular purpose del template (solo DRAFT) [DPO]")
    public void removePurpose(@PathVariable UUID id, @PathVariable UUID purposeId) {
        service.removePurpose(id, purposeId);
    }

    @GetMapping("/{id}/purposes")
    @Operation(summary = "Listar purposes del template ordenadas por ORDER_POSITION [DPO]")
    public List<TemplatePurposeResponse> listPurposes(@PathVariable UUID id) {
        return service.listPurposes(id);
    }

    @PatchMapping("/{id}/purposes/{purposeId}")
    @Operation(summary = "Actualizar ORDER_POSITION o IS_VISIBLE de una purpose en el template (solo DRAFT) [DPO]")
    public TemplatePurposeResponse updatePurpose(
            @PathVariable UUID id,
            @PathVariable UUID purposeId,
            @RequestBody UpdateTemplatePurposeRequest req) {
        return service.updatePurpose(id, purposeId, req);
    }
}
