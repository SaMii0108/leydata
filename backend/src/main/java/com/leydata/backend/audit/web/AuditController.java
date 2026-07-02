package com.leydata.backend.audit.web;

import com.leydata.backend.audit.application.dto.AgreementTraceResponse;
import com.leydata.backend.audit.application.dto.AuditLogResponseDto;
import com.leydata.backend.audit.application.dto.EntityIntegrityLogResponse;
import com.leydata.backend.audit.application.dto.VerifyIntegrityRequest;
import com.leydata.backend.audit.application.service.AgreementTraceService;
import com.leydata.backend.audit.application.service.AuditService;
import com.leydata.backend.audit.application.service.IntegrityVerifier;
import com.leydata.backend.audit.infrastructure.persistence.EntityIntegrityLogRepository;
import com.leydata.backend.audit.infrastructure.persistence.SystemAuditLogRepository;
import com.leydata.backend.entity.SystemAuditLog;
import com.leydata.backend.shared.SecurityContextHelper;
import com.leydata.backend.user.infrastructure.persistence.UsersRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Auditoría",
        description = "Log de auditoría inmutable con cadena de hashes SHA-256. Requiere rol ADMIN. " +
                "Cada registro incluye el hash del registro anterior — si alguno se altera, la cadena se rompe. " +
                "Un trigger de PostgreSQL impide además cualquier UPDATE o DELETE sobre la tabla.")
public class AuditController {

    private final SystemAuditLogRepository auditLogRepository;
    private final AuditService auditService;
    private final UsersRepository usersRepository;
    private final IntegrityVerifier integrityVerifier;
    private final EntityIntegrityLogRepository entityIntegrityLogRepository;
    private final SecurityContextHelper securityContextHelper;
    private final AgreementTraceService agreementTraceService;

    @GetMapping("/logs")
    @Operation(
            summary = "Consultar logs de auditoría con filtros [ADMIN]",
            description = """
                    Devuelve el historial de operaciones paginado. Los filtros son mutuamente excluyentes
                    (se aplica el primero que no sea nulo): `action` → `table` → `actorEmail` → todos.

                    Acciones auditadas: `CREAR_USUARIO`, `EDITAR_USUARIO`, `DESACTIVAR_USUARIO`,
                    `BLOQUEAR_USUARIO`, `CREAR_DOMINIO`, `DESACTIVAR_DOMINIO`, `CREAR_FINALIDAD`,
                    `EDITAR_FINALIDAD`, `APROBAR_SOLICITUD`, `RECHAZAR_SOLICITUD`, entre otras.

                    Si se filtra por `actorEmail` y el email no existe, devuelve lista vacía sin error
                    (para no revelar si el email existe en el sistema).
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista paginada de logs"),
            @ApiResponse(responseCode = "403", description = "Sin rol ADMIN")
    })
    public Map<String, Object> getLogs(
            @Parameter(description = "Filtrar por tipo de acción (ej: CREAR_USUARIO)")
            @RequestParam(required = false) String action,
            @Parameter(description = "Filtrar por tabla afectada (ej: users, domains, purposes)")
            @RequestParam(required = false) String table,
            @Parameter(description = "Filtrar por email del actor que realizó la acción")
            @RequestParam(required = false) String actorEmail,
            @Parameter(description = "Número de página (0-based)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Registros por página")
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<SystemAuditLog> logs;

        if (action != null && !action.isBlank()) {
            logs = auditLogRepository.findByActionOrderByCreatedAtDesc(action.toUpperCase(), pageable);
        } else if (table != null && !table.isBlank()) {
            logs = auditLogRepository.findByTableNameOrderByCreatedAtDesc(table.toLowerCase(), pageable);
        } else if (actorEmail != null && !actorEmail.isBlank()) {
            String actorId = usersRepository.findByEmail(actorEmail)
                    .map(u -> u.getKeycloakId())
                    .orElse(null);
            if (actorId == null) {
                return Map.of(
                        "status", "success",
                        "logs", List.of(),
                        "total", 0,
                        "page", page,
                        "totalPages", 0);
            }
            logs = auditLogRepository.findByActorIdOrderByCreatedAtDesc(actorId, pageable);
        } else {
            logs = auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);
        }

        Page<AuditLogResponseDto> response = logs.map(this::toDto);

        return Map.of(
                "status", "success",
                "logs", response.getContent(),
                "total", response.getTotalElements(),
                "page", response.getNumber(),
                "totalPages", response.getTotalPages());
    }

    @GetMapping("/logs/verify")
    @Operation(
            summary = "Verificar integridad de la cadena de auditoría [ADMIN]",
            description = """
                    Recorre todos los registros y verifica que la cadena de hashes SHA-256 no ha sido alterada.
                    Cada registro almacena el hash del registro anterior — si alguno fue modificado directamente
                    en la BD, el hash no coincide y la cadena se rompe.

                    Devuelve `valid: true` si la cadena está íntegra, `valid: false` con mensaje de alerta si no.
                    Este endpoint es el instrumento de verificación formal ante auditores o reguladores.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resultado de verificación (valid=true/false)"),
            @ApiResponse(responseCode = "403", description = "Sin rol ADMIN")
    })
    public Map<String, Object> verifyIntegrity() {
        boolean valid = auditService.verifyChainIntegrity();
        if (valid) {
            return Map.of(
                    "status", "success",
                    "valid", true,
                    "message", "Cadena de auditoría íntegra. Ningún registro ha sido alterado.");
        } else {
            return Map.of(
                    "status", "warning",
                    "valid", false,
                    "message", "ALERTA: Se detectaron inconsistencias en la cadena de auditoría. Posible alteración de registros.");
        }
    }

    @PostMapping("/integrity/verify")
    @Operation(summary = "Verificar integridad de una entidad bajo demanda [ADMIN]",
            description = "entityType: AGREEMENT, PURPOSE, TEMPLATE o DOCUMENT.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resultado de verificación de integridad"),
            @ApiResponse(responseCode = "400", description = "entityType inválido"),
            @ApiResponse(responseCode = "403", description = "Sin rol ADMIN")
    })
    public EntityIntegrityLogResponse verifyEntityIntegrity(@RequestBody VerifyIntegrityRequest req) {
        String checkType = req.getCheckType() != null ? req.getCheckType() : "MANUAL";
        String actorId = securityContextHelper.getKeycloakId();
        return integrityVerifier.verify(req.getEntityType(), req.getEntityId(), checkType, actorId);
    }

    @GetMapping("/integrity/log")
    @Operation(summary = "Historial de verificaciones de integridad de una entidad [ADMIN]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historial de verificaciones"),
            @ApiResponse(responseCode = "403", description = "Sin rol ADMIN")
    })
    public List<EntityIntegrityLogResponse> getEntityIntegrityLog(
            @RequestParam String entityType,
            @RequestParam UUID entityId) {
        return entityIntegrityLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId)
                .stream().map(EntityIntegrityLogResponse::from).toList();
    }

    @GetMapping("/integrity/failed")
    @Operation(summary = "Listar verificaciones de integridad fallidas [ADMIN]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de verificaciones fallidas"),
            @ApiResponse(responseCode = "403", description = "Sin rol ADMIN")
    })
    public List<EntityIntegrityLogResponse> listFailedIntegrityChecks(
            @RequestParam(required = false) String entityType) {
        List<com.leydata.backend.entity.EntityIntegrityLog> logs = (entityType != null && !entityType.isBlank())
                ? entityIntegrityLogRepository.findByIsValidFalseAndEntityType(entityType)
                : entityIntegrityLogRepository.findByIsValidFalse();
        return logs.stream().map(EntityIntegrityLogResponse::from).toList();
    }

    @GetMapping("/trace/agreement/{id}")
    @Operation(summary = "Reconstruir la cadena AGREEMENT → DOCUMENT → TEMPLATE → PURPOSES [ADMIN]",
            description = "Solo lectura — no escribe en ningún log. Verifica integridad por eslabón.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Traza completa del agreement"),
            @ApiResponse(responseCode = "403", description = "Sin rol ADMIN"),
            @ApiResponse(responseCode = "404", description = "Agreement no encontrado")
    })
    public AgreementTraceResponse traceAgreement(@PathVariable UUID id) {
        return agreementTraceService.trace(id);
    }

    private AuditLogResponseDto toDto(SystemAuditLog log) {
        return new AuditLogResponseDto(
                log.getId(),
                log.getTableName(),
                log.getRecordId(),
                log.getAction(),
                log.getOldData(),
                log.getNewData(),
                log.getActorId(),
                log.getActorRole(),
                log.getIpAddress(),
                log.getUserAgent(),
                log.getCreatedAt(),
                log.getLogHash());
    }
}
