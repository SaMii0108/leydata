package com.leydata.backend.audit;

import tools.jackson.databind.ObjectMapper;
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

//Servicio de auditoría inmutable con cadena de hashes SHA-256.
//Cada log firma el hash del log anterior (similar a una blockchain):
//si alguien modifica o elimina un registro, la cadena se rompe y la verificación lo detecta.
//La inmutabilidad física se garantiza por triggers PostgreSQL en la tabla system_audit_log.
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final SystemAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    //Self-injection con @Lazy: permite llamar a log() a través del proxy AOP de Spring.
    //Sin esto, this.log() bypassea el proxy y @Transactional(REQUIRES_NEW) no tiene efecto.
    @Autowired
    @Lazy
    private AuditService self;

    //REQUIRES_NEW: el log de auditoría se persiste en su propia transacción,
    //desacoplada de la transacción del negocio. Garantiza que el registro
    //se guarda incluso si la transacción del servicio que llama hace rollback.
    //
    //IMPORTANTE: este método NO captura excepciones. Si falla, lanza hacia el caller.
    //El caller debe envolver la llamada en try-catch para que el fallo de auditoría
    //no afecte la operación de negocio principal. Este diseño evita el antipatrón
    //de "catch + return normal" dentro de @Transactional(REQUIRES_NEW) que produce
    //UnexpectedRollbackException.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditContext context) {
        //Extraer IP y User-Agent del request HTTP actual
        String ipAddress = extractClientIp();
        String userAgent = extractUserAgent();

        //Obtener el hash del último log para encadenar (hash chain)
        //El primer registro de la historia usa "GENESIS" como hash previo
        String previousHash = auditLogRepository.findTopByOrderByCreatedAtDesc()
                .map(SystemAuditLog::getLogHash)
                .orElse("GENESIS");

        UUID logId = UUID.randomUUID();
        //Truncar a microsegundos: PostgreSQL timestamp(6) guarda hasta 6 decimales.
        //LocalDateTime.now() en Java 21/Linux puede retornar nanosegundos (9 decimales).
        //Si el hash se computa con nanosegundos pero el DB guarda microsegundos, la verificación falla.
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);

        //Serializar los estados anterior y posterior a JSON legible
        String oldDataJson = toJson(context.getOldData());
        String newDataJson = toJson(context.getNewData());

        //Calcular el hash SHA-256 que firma este registro
        String logHash = computeHash(logId, context, now, newDataJson, previousHash);

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

        //save() llama isNew() en la entidad → retorna true (via Persistable<UUID>) → persist() → INSERT.
        //Necesario porque el ID se asigna manualmente antes de persistir (para incluirlo en el hash SHA-256).
        auditLogRepository.save(auditLog);
        log.info("Auditoría registrada: accion={} tabla={} actor={}", context.getAction(), context.getTableName(), context.getActorId());
    }

    //Versión segura de log(): llama a self.log() a través del proxy AOP de Spring
    //para que @Transactional(REQUIRES_NEW) se aplique correctamente.
    //Captura cualquier excepción sin propagarla, así el fallo de auditoría
    //nunca revierte la transacción de negocio principal.
    //Versión segura de log(): llama a self.log() a través del proxy AOP de Spring
    //para que @Transactional(REQUIRES_NEW) se aplique correctamente.
    //Captura cualquier excepción sin propagarla, así el fallo de auditoría
    //nunca revierte la transacción de negocio principal.
    public void tryLog(AuditContext context) {
        try {
            self.log(context);
        } catch (Exception e) {
            log.error("[AuditService] Fallo al registrar auditoría (accion={}, tabla={}): {} - {}",
                    context.getAction(), context.getTableName(),
                    e.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    //Recorre todos los logs en orden cronológico y recomputa cada hash.
    //Si algún registro fue alterado o eliminado, la cadena se rompe y retorna false.
    @Transactional(readOnly = true)
    public boolean verifyChainIntegrity() {
        List<SystemAuditLog> logs = auditLogRepository.findAllByOrderByCreatedAtAsc();
        String expectedPreviousHash = "GENESIS";

        for (SystemAuditLog entry : logs) {
            //El previousLogHash de este registro debe coincidir con el hash del anterior
            if (!expectedPreviousHash.equals(entry.getPreviousLogHash())) {
                log.warn("Cadena de auditoría rota en registro: {}", entry.getId());
                return false;
            }

            //Recomputar el hash del registro y comparar con el almacenado
            String recomputedHash = recomputeHash(entry);
            if (!recomputedHash.equals(entry.getLogHash())) {
                log.warn("Hash inválido en registro de auditoría: {}", entry.getId());
                return false;
            }

            expectedPreviousHash = entry.getLogHash();
        }

        return true;
    }

    //Computa el hash SHA-256 de un nuevo log antes de persistirlo
    private String computeHash(UUID id, AuditContext ctx, LocalDateTime createdAt,
                               String newDataJson, String previousHash) {
        String input = String.join("|",
                id.toString(),
                ctx.getTableName(),
                ctx.getRecordId() != null ? ctx.getRecordId().toString() : "null",
                ctx.getAction(),
                ctx.getActorId() != null ? ctx.getActorId().toString() : "null",
                ctx.getActorRole() != null ? ctx.getActorRole() : "null",
                createdAt.toString(),
                newDataJson != null ? newDataJson : "null",
                previousHash);

        return sha256(input);
    }

    //Recomputa el hash de un log ya persistido para verificar que no fue alterado
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
                entry.getPreviousLogHash() != null ? entry.getPreviousLogHash() : "GENESIS");

        return sha256(input);
    }

    //Aplica SHA-256 a una cadena y retorna el resultado en formato hexadecimal lowercase
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
            //SHA-256 es obligatorio en cualquier JVM compatible con Java 21
            throw new RuntimeException("SHA-256 no disponible en este entorno JVM", e);
        }
    }

    //Serializa un objeto a JSON para almacenarlo en oldData/newData
    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("No se pudo serializar objeto a JSON para auditoría: {}", e.getMessage());
            return "{}";
        }
    }

    //Extrae la IP real del cliente considerando proxies y balanceadores de carga
    private String extractClientIp() {
        try {
            HttpServletRequest request = ((ServletRequestAttributes)
                    RequestContextHolder.currentRequestAttributes()).getRequest();
            //X-Forwarded-For contiene la IP original cuando hay un proxy o load balancer
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                //El primer elemento es la IP del cliente real
                return forwarded.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    //Extrae el User-Agent para identificar el navegador y sistema operativo del operador
    private String extractUserAgent() {
        try {
            HttpServletRequest request = ((ServletRequestAttributes)
                    RequestContextHolder.currentRequestAttributes()).getRequest();
            String ua = request.getHeader("User-Agent");
            return ua != null ? ua : "UNKNOWN";
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }
}
