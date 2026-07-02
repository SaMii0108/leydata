package com.leydata.backend.orgdomain.web;

import com.leydata.backend.orgdomain.application.dto.CreateDomainRequest;
import com.leydata.backend.orgdomain.application.dto.DomainResponse;
import com.leydata.backend.orgdomain.application.service.DomainService;
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
@RequestMapping("/api/domains")
@RequiredArgsConstructor
@Tag(name = "Dominios", description = "Áreas organizacionales de la empresa. Requiere rol ADMIN. " +
        "Los dominios nunca se eliminan físicamente para preservar la trazabilidad histórica.")
public class DomainController {

    private final DomainService domainService;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Crear dominio [ADMIN]",
            description = """
                    Crea un área organizacional. El `code` es único e inmutable (se usa como identificador en logs).

                    - `jefeId` es opcional. Si se omite, el dominio queda sin JEFE_DOMINIO hasta que se asigne uno desde la edición de usuario.
                    - No se pueden asignar como jefe usuarios sin rol `JEFE_DOMINIO`.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Dominio creado"),
            @ApiResponse(responseCode = "409", description = "Código de dominio ya registrado"),
            @ApiResponse(responseCode = "400", description = "jefeId no tiene rol JEFE_DOMINIO")
    })
    public Map<String, Object> createDomain(@RequestBody CreateDomainRequest request) {
        DomainResponse created = domainService.createDomain(request);
        return Map.of(
                "status", "success",
                "message", "Dominio creado correctamente",
                "domainId", created.getId());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/all")
    @Operation(summary = "Listar todos los dominios (incluidos inactivos) [ADMIN]",
            description = "Devuelve todos los dominios con su estado activo/inactivo y JEFE_DOMINIO asignado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de dominios"),
            @ApiResponse(responseCode = "403", description = "Sin rol ADMIN")
    })
    public Map<String, Object> getAllDomains() {
        List<DomainResponse> domains = domainService.getAllDomains();
        return Map.of("status", "success", "domains", domains);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'DPO')")
    @GetMapping("/active")
    @Operation(summary = "Listar dominios activos [ADMIN, DPO]",
            description = "Devuelve solo los dominios activos. Usado por el DPO para seleccionar dominio al crear una finalidad.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de dominios activos"),
            @ApiResponse(responseCode = "403", description = "Sin rol ADMIN o DPO")
    })
    public Map<String, Object> getActiveDomains() {
        List<DomainResponse> domains = domainService.getActiveDomains();
        return Map.of("status", "success", "domains", domains);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{domainId}/deactivate")
    @Operation(
            summary = "Desactivar dominio [ADMIN]",
            description = """
                    Marca el dominio como inactivo. No cancela solicitudes pendientes ni desactiva finalidades activas.
                    Es una señal organizacional. Las finalidades y solicitudes históricas quedan intactas.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dominio desactivado"),
            @ApiResponse(responseCode = "404", description = "Dominio no encontrado")
    })
    public Map<String, Object> deactivateDomain(@PathVariable UUID domainId) {
        DomainResponse domain = domainService.deactivateDomain(domainId);
        return Map.of(
                "status", "success",
                "message", "Dominio desactivado correctamente",
                "domainId", domain.getId());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{domainId}/reactivate")
    @Operation(summary = "Reactivar dominio [ADMIN]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dominio reactivado"),
            @ApiResponse(responseCode = "404", description = "Dominio no encontrado")
    })
    public Map<String, Object> reactivateDomain(@PathVariable UUID domainId) {
        DomainResponse domain = domainService.reactivateDomain(domainId);
        return Map.of(
                "status", "success",
                "message", "Dominio reactivado correctamente",
                "domainId", domain.getId());
    }
}
