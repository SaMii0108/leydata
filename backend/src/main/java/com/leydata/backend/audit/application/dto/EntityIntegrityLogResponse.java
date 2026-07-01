package com.leydata.backend.audit.application.dto;

import com.leydata.backend.entity.EntityIntegrityLog;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class EntityIntegrityLogResponse {
    UUID id;
    String entityType;
    UUID entityId;
    String storedHash;
    String recalculatedHash;
    Boolean isValid;
    String checkType;
    LocalDateTime createdAt;
    String errorDetail;
    String createdBy;

    public static EntityIntegrityLogResponse from(EntityIntegrityLog log) {
        return EntityIntegrityLogResponse.builder()
            .id(log.getId())
            .entityType(log.getEntityType())
            .entityId(log.getEntityId())
            .storedHash(log.getStoredHash())
            .recalculatedHash(log.getRecalculatedHash())
            .isValid(log.getIsValid())
            .checkType(log.getCheckType())
            .createdAt(log.getCreatedAt())
            .errorDetail(log.getErrorDetail())
            .createdBy(log.getCreatedBy())
            .build();
    }
}
