package com.leydata.backend.audit.application.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Value
@Builder
public class AgreementTraceResponse {
    UUID agreementId;
    UUID dataSubjectId;
    LocalDateTime createdAt;
    DocumentLink document;
    TemplateLink template;
    List<PurposeLink> purposes;
    String overallIntegrity; // OK, MISMATCH, PARTIAL

    @Value
    @Builder
    public static class DocumentLink {
        UUID documentId;
        Integer version;
        Boolean isValid;
    }

    @Value
    @Builder
    public static class TemplateLink {
        UUID templateId;
        String templateKey;
        Integer version;
        Boolean isValid;
    }

    @Value
    @Builder
    public static class PurposeLink {
        UUID purposeId;
        UUID purposeFamilyId;
        Integer version;
        String code;
        Boolean accepted;
        Boolean isValid;
        String integrityStatus; // OK, INTEGRITY_MISMATCH, UNKNOWN
    }
}
