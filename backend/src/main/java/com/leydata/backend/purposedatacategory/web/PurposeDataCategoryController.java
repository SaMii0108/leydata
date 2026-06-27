package com.leydata.backend.purposedatacategory.web;

import com.leydata.backend.purposedatacategory.application.dto.DataRetentionPolicyRequest;
import com.leydata.backend.purposedatacategory.application.dto.PurposeDataCategoryRequest;
import com.leydata.backend.purposedatacategory.application.dto.PurposeDataCategoryResponse;
import com.leydata.backend.purposedatacategory.application.service.PurposeDataCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Categorías por Finalidad + Retención",
        description = "Vincula qué tipos de datos trata una finalidad y por cuánto tiempo se retienen. " +
                "La configuración queda bloqueada (retentionLocked=true) cuando la finalidad entra en un documento PUBLISHED.")
public class PurposeDataCategoryController {

    private final PurposeDataCategoryService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('DPO','ADMIN','JEFE_DOMINIO')")
    @Operation(
            summary = "Listar categorías vinculadas a la finalidad [DPO, ADMIN, JEFE_DOMINIO]",
            description = "Devuelve las categorías con su política de retención. " +
                    "`retentionLocked=true` indica que la configuración está bloqueada por un documento PUBLISHED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de vínculos finalidad-categoría")
    })
    public List<PurposeDataCategoryResponse> listByPurpose(@PathVariable UUID purposeId) {
        return service.listByPurpose(purposeId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('DPO','ADMIN','JEFE_DOMINIO')")
    @Operation(summary = "Obtener vínculo finalidad-categoría por ID [DPO, ADMIN, JEFE_DOMINIO]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalle del vínculo"),
            @ApiResponse(responseCode = "404", description = "Vínculo no encontrado")
    })
    public PurposeDataCategoryResponse getById(@PathVariable UUID purposeId,
                                               @PathVariable UUID id) {
        return service.getById(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('DPO','ADMIN')")
    @Operation(
            summary = "Vincular categoría a finalidad + definir retención [DPO, ADMIN]",
            description = """
                    Vincula una categoría de datos a la finalidad y define su política de retención en un solo paso.
                    La retención **es obligatoria** — no puede quedar sin definir (Ley 21.719 exige declarar plazos).

                    `dataUses` válidos: `STORAGE`, `PROCESSING`, `TRANSFER_TO_THIRD_PARTIES`, `PROFILING`, `ANALYSIS`.
                    Debe declararse al menos un uso.

                    **Bloqueado si la finalidad ya está en un documento PUBLISHED** (`retentionLocked=true`).
                    Para agregar: crear nueva versión del documento con `POST /api/privacy-documents/{docId}/new-version`.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Categoría vinculada con política de retención"),
            @ApiResponse(responseCode = "409", description = "Retención bloqueada — finalidad en documento PUBLISHED"),
            @ApiResponse(responseCode = "422", description = "Finalidad no aprobada, categoría inactiva, o vínculo duplicado"),
            @ApiResponse(responseCode = "400", description = "dataUses vacío o retentionUnit inválido")
    })
    public ResponseEntity<PurposeDataCategoryResponse> link(@PathVariable UUID purposeId,
                                                            @Valid @RequestBody PurposeDataCategoryRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.link(purposeId, req));
    }

    @PutMapping("/{id}/retention")
    @PreAuthorize("hasAnyRole('DPO','ADMIN')")
    @Operation(
            summary = "Actualizar política de retención [DPO, ADMIN]",
            description = "Actualiza el plazo de retención de una categoría ya vinculada. " +
                    "Bloqueado si la finalidad está en un documento PUBLISHED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Retención actualizada"),
            @ApiResponse(responseCode = "409", description = "Retención bloqueada — documento PUBLISHED"),
            @ApiResponse(responseCode = "404", description = "Vínculo no encontrado")
    })
    public PurposeDataCategoryResponse updateRetention(@PathVariable UUID purposeId,
                                                       @PathVariable UUID id,
                                                       @Valid @RequestBody DataRetentionPolicyRequest req) {
        return service.updateRetention(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('DPO','ADMIN')")
    @Operation(
            summary = "Desvincular categoría de la finalidad [DPO, ADMIN]",
            description = "Elimina el vínculo y la política de retención asociada. " +
                    "Bloqueado si la finalidad está en un documento PUBLISHED.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Categoría desvinculada"),
            @ApiResponse(responseCode = "409", description = "Retención bloqueada — documento PUBLISHED"),
            @ApiResponse(responseCode = "404", description = "Vínculo no encontrado")
    })
    public ResponseEntity<Void> unlink(@PathVariable UUID purposeId,
                                       @PathVariable UUID id) {
        service.unlink(id);
        return ResponseEntity.noContent().build();
    }
}
