package com.leydata.orchestrator.consent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TemplateContentResponse(
        UUID templateId,
        UUID domainId,
        String templateKey,
        Integer version,
        String name,
        String title,
        String description,
        UUID documentId,
        List<PurposeItem> purposes
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PurposeItem(
            UUID purposeId,
            String purposeCode,
            String purposeName,
            String purposeDescription,
            String purposeShortDescription,
            boolean required,
            boolean revocable,
            String legalBasisCode
    ) {}
}
