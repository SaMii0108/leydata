package com.leydata.backend.agreement.application.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class PendingDeletionItem {
    String subjectIdentifier;
    UUID purposeId;
    String purposeCode;
    String purposeName;
    UUID agreementId;
    LocalDateTime expiredAt;
    boolean anonymizeAfter;
}
