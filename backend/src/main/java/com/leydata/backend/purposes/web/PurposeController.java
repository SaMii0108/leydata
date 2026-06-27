package com.leydata.backend.purposes.web;

import com.leydata.backend.purposes.application.dto.CreatePurposeRequest;
import com.leydata.backend.purposes.application.dto.PurposeResponse;
import com.leydata.backend.purposes.application.dto.UpdatePurposeRequest;
import com.leydata.backend.purposes.application.service.PurposeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/purposes")
@RequiredArgsConstructor
@Tag(name = "Finalidades",
        description = "Unidades atómicas de permiso de tratamiento de datos (Ley 21.719). " +
                "El DPO las crea y gestiona. El JEFE_DOMINIO puede verlas. " +
                "Una finalidad queda 'locked' cuando se incluye en un documento PUBLISHED.")
public class PurposeController {

    private final PurposeService purposeService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Crear finalidad [DPO]",
            description = """
                    Crea una nueva finalidad de tratamiento de datos. El `code` se normaliza a MAYÚSCULAS y es único.

                    - `purposeRequestId` es opcional pero recomendado para trazabilidad (vincula con la solicitud aprobada).
                    - `consentStatement` es el texto exacto que el titular verá y aceptará. Puede completarse después.
                    - `required: true` significa que el titular no puede rechazar esta finalidad (ej: facturación obligatoria).
                    - `revocable: true` permite que el titular retire su consentimiento en cualquier momento.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Finalidad creada"),
            @ApiResponse(responseCode = "422", description = "Código de finalidad duplicado"),
            @ApiResponse(responseCode = "400", description = "Campos obligatorios vacíos o legalBasisId no encontrado"),
            @ApiResponse(responseCode = "403", description = "Sin rol DPO o ADMIN")
    })
    public PurposeResponse create(@RequestBody @Valid CreatePurposeRequest req) {
        return purposeService.create(req);
    }

    @GetMapping
    @Operation(
            summary = "Listar finalidades activas [DPO, ADMIN, JEFE_DOMINIO]",
            description = """
                    Devuelve las finalidades activas visibles para el rol del usuario autenticado:
                    - DPO / ADMIN: todas las finalidades activas del sistema.
                    - JEFE_DOMINIO: solo las finalidades de los dominios que tiene asignados.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de finalidades")
    })
    public List<PurposeResponse> listAll() {
        return purposeService.listAll();
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Obtener finalidad por ID [DPO, ADMIN, JEFE_DOMINIO]",
            description = "El JEFE_DOMINIO solo puede ver finalidades de sus propios dominios. Retorna 403 si no pertenece.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalle de la finalidad"),
            @ApiResponse(responseCode = "403", description = "La finalidad no pertenece a los dominios del jefe"),
            @ApiResponse(responseCode = "404", description = "Finalidad no encontrada")
    })
    public PurposeResponse getById(@PathVariable UUID id) {
        return purposeService.getById(id);
    }

    @GetMapping("/domain/{domainId}")
    @Operation(summary = "Listar finalidades por dominio [DPO, ADMIN, JEFE_DOMINIO]",
            description = "El JEFE_DOMINIO solo puede listar finalidades de sus propios dominios.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de finalidades del dominio"),
            @ApiResponse(responseCode = "403", description = "Dominio no pertenece al jefe autenticado")
    })
    public List<PurposeResponse> listByDomain(@PathVariable UUID domainId) {
        return purposeService.listByDomain(domainId);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Editar finalidad [DPO]",
            description = """
                    Actualiza la finalidad. **Bloqueado si está incluida en un documento PUBLISHED** (`locked: true`).

                    Para editarla: crear nueva versión del documento con `POST /api/privacy-documents/{docId}/new-version`.
                    El nuevo DRAFT permite editar la finalidad libremente hasta que se vuelva a publicar.

                    Campos no editables: `code`, `domainId`.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Finalidad actualizada"),
            @ApiResponse(responseCode = "422", description = "Finalidad bloqueada por documento PUBLISHED"),
            @ApiResponse(responseCode = "404", description = "Finalidad no encontrada")
    })
    public PurposeResponse update(@PathVariable UUID id,
                                  @RequestBody @Valid UpdatePurposeRequest req) {
        return purposeService.update(id, req);
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Desactivar finalidad (soft delete) [DPO]",
            description = """
                    Desactiva la finalidad. No la elimina — queda en el historial de documentos publicados.

                    **Prerequisito:** la finalidad no puede tener categorías de datos activas vinculadas.
                    Desvincular primero con `DELETE /api/purposes/{id}/data-categories/{pdcId}`.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Finalidad desactivada"),
            @ApiResponse(responseCode = "422", description = "La finalidad tiene categorías de datos activas"),
            @ApiResponse(responseCode = "404", description = "Finalidad no encontrada")
    })
    public PurposeResponse deactivate(@PathVariable UUID id) {
        return purposeService.deactivate(id);
    }
}
