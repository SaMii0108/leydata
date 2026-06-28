package com.leydata.backend.agreement.web;

import com.leydata.backend.agreement.application.dto.AgreementIntegrityLogResponse;
import com.leydata.backend.agreement.application.dto.AgreementResponse;
import com.leydata.backend.agreement.application.dto.CreateAgreementRequest;
import com.leydata.backend.agreement.application.dto.VerifyIntegrityRequest;
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

    @PostMapping("/{id}/verify-integrity")
    @Operation(summary = "Verificar integridad de un agreement bajo demanda")
    public AgreementIntegrityLogResponse verifyIntegrity(
            @PathVariable UUID id,
            @RequestBody(required = false) VerifyIntegrityRequest req) {
        String checkType = (req != null && req.getCheckType() != null) ? req.getCheckType() : "MANUAL";
        // createdBy queda null hasta que se defina el modelo de autenticación de este módulo
        return service.verifyIntegrity(id, checkType, null);
    }

    @GetMapping("/{id}/integrity-log")
    @Operation(summary = "Historial de verificaciones de integridad del agreement")
    public List<AgreementIntegrityLogResponse> getIntegrityLog(@PathVariable UUID id) {
        return service.getIntegrityLog(id);
    }

    @GetMapping("/integrity-log/failed")
    @Operation(summary = "Listar verificaciones de integridad fallidas (IS_VALID = false) para investigación")
    public List<AgreementIntegrityLogResponse> listFailedVerifications() {
        return service.listFailedVerifications();
    }
}
