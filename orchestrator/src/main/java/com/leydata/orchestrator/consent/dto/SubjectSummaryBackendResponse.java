package com.leydata.orchestrator.consent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SubjectSummaryBackendResponse(
        String subjectIdentifier,
        UUID domainId,
        UUID agreementId,
        UUID templateId,
        String templateKey,
        Integer templateVersion,
        UUID documentId,
        List<PurposeItem> purposes
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PurposeItem(
            UUID purposeId,
            String purposeCode,
            String purposeName,
            boolean accepted,
            String status,
            LocalDateTime expiresAt,
            LocalDateTime acceptedAt,
            boolean required,
            boolean revocable
    ) {}
}
