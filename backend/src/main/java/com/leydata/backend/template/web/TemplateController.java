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

import com.leydata.backend.template.application.dto.ActivateTemplateRequest;
import com.leydata.backend.template.application.dto.AddTemplatePurposeRequest;
import com.leydata.backend.template.application.dto.CreateTemplateRequest;
import com.leydata.backend.template.application.dto.TemplatePurposeResponse;
import com.leydata.backend.template.application.dto.TemplateResolutionResponse;
import com.leydata.backend.template.application.dto.TemplateResponse;
import com.leydata.backend.template.application.dto.TemplateVerifyResponse;
import com.leydata.backend.template.application.dto.UpdateTemplatePurposeRequest;
import com.leydata.backend.template.application.service.TemplateService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('DPO','ADMIN')")
@Tag(name = "Templates", description = "Gestión de plantillas de consentimiento [DPO, ADMIN]")
public class TemplateController {

    private final TemplateService service;

    // ── CRUD ─────────────────────────────────────────────────────────────────────

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear template en DRAFT [DPO, ADMIN]")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Template creado en DRAFT"),
            @ApiResponse(responseCode = "400", description = "Request inválido"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Sin rol DPO o ADMIN")
    })
    public TemplateResponse create(@Valid @RequestBody CreateTemplateRequest req) {
        return service.create(req);
    }

    @PostMapping("/{id}/new-version")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear nueva versión del template (mismo TEMPLATE_KEY) [DPO, ADMIN]")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Nueva versión creada en DRAFT"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Sin rol DPO o ADMIN"),
            @ApiResponse(responseCode = "404", description = "Template no encontrado")
    })
    public TemplateResponse newVersion(@PathVariable UUID id) {
        return service.newVersion(id);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener template por ID [DPO, ADMIN]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Template encontrado"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Sin rol DPO o ADMIN"),
            @ApiResponse(responseCode = "404", description = "Template no encontrado")
    })
    public TemplateResponse getById(@PathVariable UUID id) {
        return service.getById(id);
    }

    @GetMapping
    @Operation(summary = "Listar templates con filtros opcionales [DPO, ADMIN]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de templates"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Sin rol DPO o ADMIN")
    })
    public List<TemplateResponse> list(
            @RequestParam(required = false) UUID domainId,
            @RequestParam(required = false) String templateKey,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) String createdBy,
            @RequestParam(required = false) String approvedBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdAfter,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdBefore) {
        return service.list(domainId, templateKey, isActive, createdBy, approvedBy, createdAfter, createdBefore);
    }

    @GetMapping("/family/{templateKey}")
    @Operation(summary = "Historial de versiones de un TEMPLATE_KEY dentro de un dominio [DPO, ADMIN]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de versiones del template"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Sin rol DPO o ADMIN")
    })
    public List<TemplateResponse> getHistory(@PathVariable String templateKey, @RequestParam UUID domainId) {
        return service.getHistory(domainId, templateKey);
    }

    @GetMapping("/active/{templateKey}")
    @Operation(summary = "Obtener la versión activa de un TEMPLATE_KEY dentro de un dominio [DPO, ADMIN]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Template activo"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Sin rol DPO o ADMIN"),
            @ApiResponse(responseCode = "404", description = "No hay versión activa para ese templateKey")
    })
    public TemplateResponse getActive(@PathVariable String templateKey, @RequestParam UUID domainId) {
        return service.getActive(domainId, templateKey);
    }

    // ── B2B (uso interno — Orquestador) ─────────────────────────────────────────────

    @GetMapping("/resolve")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Resolver template activo + documento publicado por templateKey y domainId " +
            "[uso interno B2B — llamado por el Orquestador con su identidad de servicio]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Template resuelto con documento publicado"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "404", description = "Template activo o documento publicado no encontrado")
    })
    public TemplateResolutionResponse resolve(@RequestParam UUID domainId, @RequestParam String templateKey) {
        return service.resolveForCapture(domainId, templateKey);
    }

    // ── INTEGRIDAD ───────────────────────────────────────────────────────────────

    @GetMapping("/{id}/verify")
    @Operation(summary = "Verificar integridad SHA-256 del template activado [DPO, ADMIN]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resultado de verificación de integridad"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Sin rol DPO o ADMIN"),
            @ApiResponse(responseCode = "404", description = "Template no encontrado")
    })
    public TemplateVerifyResponse verify(@PathVariable UUID id) {
        return service.verify(id);
    }

    // ── WORKFLOW ─────────────────────────────────────────────────────────────────

    @PostMapping("/{id}/approve")
    @Operation(summary = "Aprobar template [DPO, ADMIN]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Template aprobado"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Sin rol DPO o ADMIN"),
            @ApiResponse(responseCode = "404", description = "Template no encontrado")
    })
    public TemplateResponse approve(@PathVariable UUID id) {
        return service.approve(id);
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Activar template — desactiva la versión anterior del mismo TEMPLATE_KEY [DPO, ADMIN]",
               description = "forceReconsent=true obliga a todos los titulares con acuerdos en versiones anteriores a re-consentir (Ley 21.719 cambios sustanciales)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Template activado"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Sin rol DPO o ADMIN"),
            @ApiResponse(responseCode = "404", description = "Template no encontrado")
    })
    public TemplateResponse activate(@PathVariable UUID id,
                                     @RequestBody(required = false) ActivateTemplateRequest req) {
        boolean forceReconsent = req != null && Boolean.TRUE.equals(req.getForceReconsent());
        return service.activate(id, forceReconsent);
    }

    // ── PURPOSES ─────────────────────────────────────────────────────────────────

    @PostMapping("/{id}/purposes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Vincular purpose al template (solo DRAFT) [DPO, ADMIN]")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Purpose vinculada"),
            @ApiResponse(responseCode = "400", description = "Template no está en DRAFT"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Sin rol DPO o ADMIN")
    })
    public void addPurpose(@PathVariable UUID id, @Valid @RequestBody AddTemplatePurposeRequest req) {
        service.addPurpose(id, req);
    }

    @DeleteMapping("/{id}/purposes/{purposeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Desvincular purpose del template (solo DRAFT) [DPO, ADMIN]")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Purpose desvinculada"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Sin rol DPO o ADMIN"),
            @ApiResponse(responseCode = "404", description = "Template o purpose no encontrado")
    })
    public void removePurpose(@PathVariable UUID id, @PathVariable UUID purposeId) {
        service.removePurpose(id, purposeId);
    }

    @GetMapping("/{id}/purposes")
    @Operation(summary = "Listar purposes del template ordenadas por ORDER_POSITION [DPO, ADMIN]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de purposes del template"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Sin rol DPO o ADMIN")
    })
    public List<TemplatePurposeResponse> listPurposes(@PathVariable UUID id) {
        return service.listPurposes(id);
    }

    @PatchMapping("/{id}/purposes/{purposeId}")
    @Operation(summary = "Actualizar ORDER_POSITION o IS_VISIBLE de una purpose en el template (solo DRAFT) [DPO, ADMIN]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Purpose actualizada"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Sin rol DPO o ADMIN"),
            @ApiResponse(responseCode = "404", description = "Template o purpose no encontrado")
    })
    public TemplatePurposeResponse updatePurpose(
            @PathVariable UUID id,
            @PathVariable UUID purposeId,
            @RequestBody UpdateTemplatePurposeRequest req) {
        return service.updatePurpose(id, purposeId, req);
    }
}
