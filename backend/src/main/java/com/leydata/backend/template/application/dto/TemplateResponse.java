package com.leydata.backend.template.application.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.leydata.backend.entity.Templates;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TemplateResponse {
    UUID id;
    UUID domainId;
    String templateKey;
    Integer version;
    String name;
    String description;
    String title;
    Boolean isActive;
    String status;
    String changeReason;
    // Keycloak IDs (String) en lugar de UUID locales
    String createdBy;
    OffsetDateTime createdAt;
    String approvedBy;
    OffsetDateTime approvedAt;
    OffsetDateTime activationDate;
    Boolean forceReconsent;

    public static TemplateResponse from(Templates t) {
        return TemplateResponse.builder()
            .id(t.getId())
            .domainId(t.getDomainId())
            .templateKey(t.getTemplateKey())
            .version(t.getVersion())
            .name(t.getName())
            .description(t.getDescription())
            .title(t.getTitle())
            .isActive(t.getIsActive())
            .status(resolveStatus(t))
            .changeReason(t.getChangeReason())
            .createdBy(t.getCreatedBy())
            .createdAt(t.getCreatedAt())
            .approvedBy(t.getApprovedBy())
            .approvedAt(t.getApprovedAt())
            .activationDate(t.getActivationDate())
            .forceReconsent(t.getForceReconsent())
            .build();
    }

    private static String resolveStatus(Templates t) {
        if (Boolean.TRUE.equals(t.getIsActive())) return "ACTIVE";
        if (t.getApprovedBy() != null) return "APPROVED";
        return "DRAFT";
    }
}
