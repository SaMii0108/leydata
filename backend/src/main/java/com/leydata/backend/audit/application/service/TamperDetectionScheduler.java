package com.leydata.backend.audit.application.service;

import com.leydata.backend.audit.application.dto.AuditContext;
import com.leydata.backend.audit.infrastructure.persistence.DbTamperLogRepository;
import com.leydata.backend.entity.DbTamperLog;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

// Detecta cambios directos en la BD (vía pgAdmin, psql, scripts SQL) que
// bypasean la API y por tanto no generan audit log.
// El trigger de PostgreSQL (V8) escribe en db_tamper_log; este scheduler lo
// eleva al audit chain oficial con actor_role = "DIRECT_DB".
@Slf4j
@Component
@RequiredArgsConstructor
public class TamperDetectionScheduler {

    private final DbTamperLogRepository tamperLogRepository;
    private final AuditService auditService;
    private final MeterRegistry meterRegistry;

    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void detectAndElevate() {
        List<DbTamperLog> pending = tamperLogRepository.findByProcessedFalseOrderByDetectedAtAsc();
        if (pending.isEmpty()) return;

        log.warn("[TAMPER] Se detectaron {} mutación(es) directa(s) en la BD. Elevando al audit log.", pending.size());

        for (DbTamperLog entry : pending) {
            try {
                auditService.log(AuditContext.builder()
                        .tableName(entry.getTableName())
                        .recordId(entry.getRecordId())
                        .action("TAMPER_DIRECTO_BD")
                        .actorId(entry.getDbUser())
                        .actorRole("DIRECT_DB")
                        .oldData(Map.of(
                                "raw", entry.getOldData() != null ? entry.getOldData() : "null",
                                "operation", entry.getOperation(),
                                "detectedAt", entry.getDetectedAt().toString()
                        ))
                        .newData(entry.getNewData() != null
                                ? Map.of("raw", entry.getNewData())
                                : Map.of("operation", "DELETE"))
                        .build());

                entry.setProcessed(true);
                tamperLogRepository.save(entry);

                meterRegistry.counter("audit.tamper.detected",
                        "table", entry.getTableName(),
                        "operation", entry.getOperation()).increment();

                log.warn("[TAMPER] agreement_id={} tabla={} operacion={} usuario_bd={}",
                        entry.getRecordId(), entry.getTableName(),
                        entry.getOperation(), entry.getDbUser());

            } catch (Exception e) {
                log.error("[TAMPER] Error elevando entrada id={}: {}", entry.getId(), e.getMessage(), e);
            }
        }
    }
}
