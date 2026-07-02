package com.leydata.backend.config;

import com.leydata.backend.audit.infrastructure.persistence.SystemAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuditHealthIndicator implements HealthIndicator {

    private final SystemAuditLogRepository auditLogRepository;

    @Override
    public Health health() {
        try {
            long count = auditLogRepository.count();
            return Health.up()
                    .withDetail("totalRecords", count)
                    .withDetail("lastRecord", auditLogRepository.findTopByOrderByCreatedAtDesc()
                            .map(log -> log.getCreatedAt().toString())
                            .orElse("none"))
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
