package com.leydata.backend.audit.web;

import com.leydata.backend.audit.application.dto.AuditLogResponseDto;
import com.leydata.backend.audit.application.service.AuditService;
import com.leydata.backend.audit.infrastructure.persistence.SystemAuditLogRepository;
import com.leydata.backend.entity.SystemAuditLog;
import com.leydata.backend.user.infrastructure.persistence.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// Endpoints de consulta del log de auditoría.
// Solo accesible por el ADMIN: los logs contienen información sensible de operaciones.
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    private final SystemAuditLogRepository auditLogRepository;
    private final AuditService auditService;
    private final UsersRepository usersRepository;

    // GET /api/audit/logs — consulta paginada con filtros opcionales
    // Parámetros opcionales: action, table, actorEmail, page, size
    @GetMapping("/logs")
    public Map<String, Object> getLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String table,
            @RequestParam(required = false) String actorEmail,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<SystemAuditLog> logs;

        if (action != null && !action.isBlank()) {
            logs = auditLogRepository.findByActionOrderByCreatedAtDesc(action.toUpperCase(), pageable);

        } else if (table != null && !table.isBlank()) {
            logs = auditLogRepository.findByTableNameOrderByCreatedAtDesc(table.toLowerCase(), pageable);

        } else if (actorEmail != null && !actorEmail.isBlank()) {
            UUID actorId = usersRepository.findByEmail(actorEmail)
                    .map(u -> u.getId())
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

    // GET /api/audit/logs/verify — verifica que la cadena de hashes no ha sido alterada
    // Útil para auditorías formales: demuestra integridad ante reguladores (Ley 21.719)
    @GetMapping("/logs/verify")
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
