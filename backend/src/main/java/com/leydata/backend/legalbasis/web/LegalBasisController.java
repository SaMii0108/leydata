package com.leydata.backend.legalbasis.web;

import com.leydata.backend.legalbasis.application.dto.LegalBasisResponse;
import com.leydata.backend.legalbasis.infrastructure.persistence.LegalBasisRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/legal-basis")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('DPO','ADMIN','JEFE_DOMINIO')")
@Tag(name = "Bases de Licitud",
        description = "Catálogo de solo lectura — los fundamentos legales válidos para tratar datos personales " +
                "bajo la Ley 21.719. La base elegida al crear una finalidad determina si requiere consentimiento activo del titular.")
public class LegalBasisController {

    private final LegalBasisRepository repo;

    @GetMapping
    @Operation(
            summary = "Listar todas las bases de licitud activas [DPO, ADMIN, JEFE_DOMINIO]",
            description = """
                    Devuelve el catálogo completo de bases de licitud definidas por la Ley 21.719.

                    El campo `consentRequired` es clave:
                    - `true` → la finalidad requiere consentimiento explícito del titular (ej: CONSENTIMIENTO).
                    - `false` → el titular solo recibe notificación, sin necesidad de aceptar activamente (ej: CONTRATO, OBLIGACION_LEGAL).
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Catálogo de bases de licitud")
    })
    public List<LegalBasisResponse> listAll() {
        return repo.findByIsActiveTrue().stream()
                .map(LegalBasisResponse::from)
                .toList();
    }

    @GetMapping("/consent")
    @Operation(
            summary = "Bases que requieren consentimiento explícito [DPO, ADMIN, JEFE_DOMINIO]",
            description = "Filtra las bases donde `consentRequired = true`. El frontend usa este endpoint para " +
                    "destacar visualmente qué finalidades van a requerir una captura activa del titular.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bases con consentimiento obligatorio")
    })
    public List<LegalBasisResponse> listConsentBased() {
        return repo.findByConsentRequiredAndIsActiveTrue(true).stream()
                .map(LegalBasisResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener base de licitud por ID [DPO, ADMIN, JEFE_DOMINIO]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalle de la base de licitud"),
            @ApiResponse(responseCode = "404", description = "Base de licitud no encontrada")
    })
    public LegalBasisResponse getById(@PathVariable UUID id) {
        return repo.findById(id)
                .map(LegalBasisResponse::from)
                .orElseThrow(() -> new java.util.NoSuchElementException("Base de licitud no encontrada: " + id));
    }
}
