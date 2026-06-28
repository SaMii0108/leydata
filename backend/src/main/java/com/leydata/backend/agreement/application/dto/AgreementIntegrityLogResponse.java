package com.leydata.backend.agreement.application.dto;

import com.leydata.backend.entity.AgreementIntegrityLog;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class AgreementIntegrityLogResponse {
    UUID id;
    UUID agreementId;
    String storedHash;
    String recalculatedHash;
    Boolean isValid;
    String checkType;
    LocalDateTime createdAt;
    String errorDetail;
    UUID createdBy;

    public static AgreementIntegrityLogResponse from(AgreementIntegrityLog log) {
        return AgreementIntegrityLogResponse.builder()
            .id(log.getId())
            .agreementId(log.getAgreementId())
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
