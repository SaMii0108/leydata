package com.leydata.backend.purpose.web;

import com.leydata.backend.purpose.application.dto.PurposeRequestDto;
import com.leydata.backend.purpose.application.dto.PurposeRequestSummaryDto;
import com.leydata.backend.purpose.application.dto.ReviewRequestDto;
import com.leydata.backend.purpose.application.service.PurposeRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Solicitudes de Finalidad",
        description = "Workflow JEFE_DOMINIO → DPO. El jefe propone una actividad de tratamiento de datos, " +
                "el DPO la aprueba o rechaza. Si se aprueba, se crea automáticamente la Finalidad (Purpose).")
public class PurposeRequestController {

    private final PurposeRequestService purposeRequestService;

    @PreAuthorize("hasRole('JEFE_DOMINIO')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Crear solicitud de finalidad [JEFE_DOMINIO]",
            description = """
                    El JEFE_DOMINIO propone una nueva actividad de tratamiento de datos para un dominio que tiene asignado.

                    Reglas:
                    - Solo puede crear solicitudes para sus propios dominios
                    - No puede crear dos solicitudes con el mismo título para el mismo dominio mientras la primera siga en PENDING
                    - El dominio debe estar activo
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Solicitud creada y enviada al DPO"),
            @ApiResponse(responseCode = "400", description = "Dominio no pertenece al jefe o está desactivado"),
            @ApiResponse(responseCode = "409", description = "Solicitud duplicada en estado PENDING")
    })
    public Map<String, Object> createPurposeRequest(@RequestBody PurposeRequestDto request) {
        PurposeRequestSummaryDto created = purposeRequestService.createPurposeRequest(request);
        return Map.of(
                "status", "success",
                "message", "Solicitud enviada al DPO correctamente",
                "request", created);
    }

    @PreAuthorize("hasRole('JEFE_DOMINIO')")
    @GetMapping("/my")
    @Operation(
            summary = "Mis solicitudes [JEFE_DOMINIO]",
            description = "Lista todas las solicitudes del jefe autenticado con su estado actual (PENDING / APPROVED / REJECTED).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de solicitudes propias")
    })
    public Map<String, Object> getMyRequests() {
        List<PurposeRequestSummaryDto> requests = purposeRequestService.getMyRequests();
        return Map.of("status", "success", "requests", requests);
    }

    @PreAuthorize("hasAnyRole('DPO', 'ADMIN')")
    @GetMapping("/pending")
    @Operation(
            summary = "Solicitudes pendientes de revisión [DPO, ADMIN]",
            description = "Lista todas las solicitudes en estado PENDING que esperan decisión del DPO.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de solicitudes pendientes")
    })
    public Map<String, Object> getPendingRequests() {
        List<PurposeRequestSummaryDto> requests = purposeRequestService.getPendingRequests();
        return Map.of("status", "success", "requests", requests);
    }

    @PreAuthorize("hasAnyRole('DPO', 'ADMIN')")
    @GetMapping
    @Operation(
            summary = "Todas las solicitudes [DPO, ADMIN]",
            description = "Lista todas las solicitudes independientemente de su estado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista completa de solicitudes")
    })
    public Map<String, Object> getAllRequests() {
        List<PurposeRequestSummaryDto> requests = purposeRequestService.getAllRequests();
        return Map.of("status", "success", "requests", requests);
    }

    @PreAuthorize("hasRole('DPO')")
    @PatchMapping("/{requestId}/review")
    @Operation(
            summary = "Revisar solicitud: aprobar o rechazar [DPO]",
            description = """
                    El DPO toma una decisión sobre la solicitud.

                    - **APPROVED** → se crea automáticamente la Finalidad (Purpose) con los datos de la solicitud.
                    - **REJECTED** → `reviewNotes` es obligatorio (Ley 21.719 exige transparencia).
                    - Una vez revisada, la solicitud no puede modificarse. Para reenviarla, el JEFE_DOMINIO debe crear una nueva.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Solicitud revisada"),
            @ApiResponse(responseCode = "400", description = "Rechazo sin motivo en reviewNotes"),
            @ApiResponse(responseCode = "409", description = "Solicitud ya fue revisada anteriormente"),
            @ApiResponse(responseCode = "404", description = "Solicitud no encontrada")
    })
    public Map<String, Object> reviewRequest(
            @PathVariable UUID requestId,
            @RequestBody ReviewRequestDto reviewRequest) {
        PurposeRequestSummaryDto reviewed = purposeRequestService.reviewRequest(requestId, reviewRequest);
        return Map.of(
                "status", "success",
                "message", "Solicitud revisada correctamente",
                "request", reviewed);
    }
}
