package com.leydata.backend.audit.application.service;

import tools.jackson.databind.ObjectMapper;
import com.leydata.backend.audit.application.dto.AuditContext;
import com.leydata.backend.audit.infrastructure.persistence.SystemAuditLogRepository;
import com.leydata.backend.entity.SystemAuditLog;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

// Servicio de auditoría inmutable con cadena de hashes SHA-256.
// Cada log firma el hash del log anterior (similar a una blockchain):
// si alguien modifica o elimina un registro, la cadena se rompe y la verificación lo detecta.
// La inmutabilidad física se garantiza por triggers PostgreSQL en la tabla system_audit_log.
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final SystemAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    // Self-injection con @Lazy: permite llamar a log() a través del proxy AOP de
    // Spring.
    // Sin esto, this.log() bypassea el proxy y @Transactional(REQUIRES_NEW) no
    // tiene efecto.
    @Autowired
    @Lazy
    private AuditService self;

    // REQUIRED: el log de auditoría se persiste en la MISMA transacción que la
    // operación de negocio.
    // Si el log falla → toda la transacción hace rollback → la operación no se
    // completa.
    // Si la operación falla antes de llegar aquí → no se crea el log.
    // Garantía de atomicidad: sin log de auditoría no hay operación (Ley 21.719).
    @Transactional(propagation = Propagation.REQUIRED)
    public void log(AuditContext context) {
        String ipAddress = extractClientIp();
        String userAgent = extractUserAgent();

        // El primer registro de la historia usa "GENESIS" como hash previo
        String previousHash = auditLogRepository.findTopByOrderByCreatedAtDesc()
                .map(SystemAuditLog::getLogHash)
                .orElse("GENESIS");

        UUID logId = UUID.randomUUID();
        // Truncar a microsegundos: PostgreSQL timestamp(6) guarda hasta 6 decimales.
        // LocalDateTime.now() en Java 21/Linux puede retornar nanosegundos (9
        // decimales).
        // Si el hash se computa con nanosegundos pero el DB guarda microsegundos, la
        // verificación falla.
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);

        String oldDataJson = toJson(context.getOldData());
        String newDataJson = toJson(context.getNewData());

        String logHash = computeHash(logId, context, now, newDataJson, previousHash, ipAddress);

        SystemAuditLog auditLog = new SystemAuditLog();
        auditLog.setId(logId);
        auditLog.setTableName(context.getTableName());
        auditLog.setRecordId(context.getRecordId());
        auditLog.setAction(context.getAction());
        auditLog.setOldData(oldDataJson);
        auditLog.setNewData(newDataJson);
        auditLog.setActorId(context.getActorId());
        auditLog.setActorRole(context.getActorRole());
        auditLog.setIpAddress(ipAddress);
        auditLog.setUserAgent(userAgent);
        auditLog.setCreatedAt(now);
        auditLog.setLogHash(logHash);
        auditLog.setPreviousLogHash(previousHash);

        // save() llama isNew() en la entidad → retorna true (via Persistable<UUID>) →
        // persist() → INSERT.
        // Necesario porque el ID se asigna manualmente antes de persistir (para
        // incluirlo en el hash SHA-256).
        auditLogRepository.save(auditLog);
        log.info("Auditoría registrada: accion={} tabla={} actor={}", context.getAction(), context.getTableName(),
                context.getActorId());
    }

    // tryLog() se conserva únicamente para contextos donde la auditoría es opcional
    // (por ejemplo, registrar intentos fallidos fuera de una transacción activa).
    // Para todas las operaciones de negocio usar log() directamente.
    public void tryLog(AuditContext context) {
        try {
            self.log(context);
        } catch (Exception e) {
            log.error("[AuditService] Fallo al registrar auditoría (accion={}, tabla={}): {} - {}",
                    context.getAction(), context.getTableName(),
                    e.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    // Recorre todos los logs en orden cronológico y recomputa cada hash.
    // Si algún registro fue alterado o eliminado, la cadena se rompe y retorna
    // false.
    @Transactional(readOnly = true)
    public boolean verifyChainIntegrity() {
        List<SystemAuditLog> logs = auditLogRepository.findAllByOrderByCreatedAtAsc();
        String expectedPreviousHash = "GENESIS";

        for (SystemAuditLog entry : logs) {
            if (!expectedPreviousHash.equals(entry.getPreviousLogHash())) {
                log.warn("Cadena de auditoría rota en registro: {}", entry.getId());
                return false;
            }

            String recomputedHash = recomputeHash(entry);
            if (!recomputedHash.equals(entry.getLogHash())) {
                log.warn("Hash inválido en registro de auditoría: {}", entry.getId());
                return false;
            }

            expectedPreviousHash = entry.getLogHash();
        }

        return true;
    }

    private String computeHash(UUID id, AuditContext ctx, LocalDateTime createdAt,
            String newDataJson, String previousHash, String ipAddress) {
        String input = String.join("|",
                id.toString(),
                ctx.getTableName(),
                ctx.getRecordId() != null ? ctx.getRecordId().toString() : "null",
                ctx.getAction(),
                ctx.getActorId() != null ? ctx.getActorId().toString() : "null",
                ctx.getActorRole() != null ? ctx.getActorRole() : "null",
                createdAt.toString(),
                newDataJson != null ? newDataJson : "null",
                previousHash,
                ipAddress != null ? ipAddress : "UNKNOWN");

        return sha256(input);
    }

    private String recomputeHash(SystemAuditLog entry) {
        String input = String.join("|",
                entry.getId().toString(),
                entry.getTableName(),
                entry.getRecordId() != null ? entry.getRecordId().toString() : "null",
                entry.getAction(),
                entry.getActorId() != null ? entry.getActorId().toString() : "null",
                entry.getActorRole() != null ? entry.getActorRole() : "null",
                entry.getCreatedAt().toString(),
                entry.getNewData() != null ? entry.getNewData() : "null",
                entry.getPreviousLogHash() != null ? entry.getPreviousLogHash() : "GENESIS",
                entry.getIpAddress() != null ? entry.getIpAddress() : "UNKNOWN");

        return sha256(input);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 es obligatorio en cualquier JVM compatible con Java 21
            throw new RuntimeException("SHA-256 no disponible en este entorno JVM", e);
        }
    }

    private String toJson(Object obj) {
        if (obj == null)
            return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("No se pudo serializar objeto a JSON para auditoría: {}", e.getMessage());
            return "{}";
        }
    }

    private String extractClientIp() {
        try {
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                    .getRequest();
            // Usamos remoteAddr (IP de la conexión TCP real) para que la IP en el log sea
            // inmutable: un cliente no puede falsificar X-Forwarded-For para alterar el registro.
            return request.getRemoteAddr();
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    private String extractUserAgent() {
        try {
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                    .getRequest();
            String ua = request.getHeader("User-Agent");
            return ua != null ? ua : "UNKNOWN";
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }
}
