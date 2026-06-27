package com.leydata.backend.datacategory.web;

import com.leydata.backend.datacategory.application.dto.DataCategoryRequest;
import com.leydata.backend.datacategory.application.dto.DataCategoryResponse;
import com.leydata.backend.datacategory.application.service.DataCategoryService;
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
@RequestMapping("/api/data-categories")
@RequiredArgsConstructor
@Tag(name = "Categorías de Datos",
        description = "Catálogo de tipos de datos personales tratados. Híbrido: 15 categorías del sistema " +
                "(Art. 16 Ley 21.719, inmutables) + categorías custom que el DPO puede crear.")
public class DataCategoryController {

    private final DataCategoryService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('DPO','ADMIN','JEFE_DOMINIO')")
    @Operation(
            summary = "Listar todas las categorías activas [DPO, ADMIN, JEFE_DOMINIO]",
            description = "Devuelve categorías del sistema (`isSystem=true`) y categorías custom activas. " +
                    "El campo `isSensitive=true` indica categorías especiales con protección reforzada (Art. 16 Ley 21.719).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de categorías")
    })
    public List<DataCategoryResponse> listAll() {
        return service.listAll();
    }

    @GetMapping("/sensitive")
    @PreAuthorize("hasAnyRole('DPO','ADMIN','JEFE_DOMINIO')")
    @Operation(
            summary = "Listar categorías sensibles [DPO, ADMIN, JEFE_DOMINIO]",
            description = "Filtra solo las categorías marcadas como sensibles: SALUD, BIOMETRICO, GENETICO, " +
                    "VIDA_SEXUAL, RELIGION, POLITICO, SINDICAL, RACIAL.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de categorías sensibles")
    })
    public List<DataCategoryResponse> listSensitive() {
        return service.listSensitive();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('DPO','ADMIN','JEFE_DOMINIO')")
    @Operation(summary = "Obtener categoría por ID [DPO, ADMIN, JEFE_DOMINIO]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalle de la categoría"),
            @ApiResponse(responseCode = "404", description = "Categoría no encontrada")
    })
    public DataCategoryResponse getById(@PathVariable UUID id) {
        return service.getById(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('DPO','ADMIN')")
    @Operation(
            summary = "Crear categoría custom [DPO, ADMIN]",
            description = "Crea una categoría personalizada de la organización. El `code` se normaliza a MAYÚSCULAS y es único.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Categoría creada"),
            @ApiResponse(responseCode = "409", description = "Código de categoría ya existe"),
            @ApiResponse(responseCode = "400", description = "Campos vacíos o inválidos")
    })
    public ResponseEntity<DataCategoryResponse> create(@Valid @RequestBody DataCategoryRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('DPO','ADMIN')")
    @Operation(
            summary = "Editar categoría custom [DPO, ADMIN]",
            description = "Solo aplica a categorías no sistema (`isSystem=false`). " +
                    "Las categorías del sistema (definidas por Ley 21.719) son inmutables.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categoría actualizada"),
            @ApiResponse(responseCode = "422", description = "Categoría del sistema — no se puede modificar"),
            @ApiResponse(responseCode = "404", description = "Categoría no encontrada")
    })
    public DataCategoryResponse update(@PathVariable UUID id,
                                       @Valid @RequestBody DataCategoryRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('DPO','ADMIN')")
    @Operation(
            summary = "Desactivar categoría (soft delete) [DPO, ADMIN]",
            description = """
                    Desactiva la categoría. No la elimina — puede existir en documentos publicados históricos.

                    Condiciones para desactivar:
                    - No debe ser una categoría del sistema (`isSystem=false`)
                    - No debe estar vinculada a ninguna finalidad activa

                    Si está vinculada: primero usar `DELETE /api/purposes/{id}/data-categories/{pdcId}` por cada vínculo activo.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categoría desactivada"),
            @ApiResponse(responseCode = "422", description = "Categoría del sistema o con finalidades activas vinculadas"),
            @ApiResponse(responseCode = "404", description = "Categoría no encontrada")
    })
    public DataCategoryResponse deactivate(@PathVariable UUID id) {
        return service.deactivate(id);
    }
}
