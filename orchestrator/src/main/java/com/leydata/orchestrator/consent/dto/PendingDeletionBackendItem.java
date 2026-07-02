package com.leydata.orchestrator.consent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PendingDeletionBackendItem(
        String subjectIdentifier,
        UUID purposeId,
        String purposeCode,
        String purposeName,
        UUID agreementId,
        LocalDateTime expiredAt,
        boolean anonymizeAfter
) {}
