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
    String templateKey;
    Integer version;
    String name;
    String description;
    String title;
    Boolean isActive;
    String status;
    String changeReason;
    UUID createdBy;
    OffsetDateTime createdAt;
    UUID approvedBy;
    OffsetDateTime approvedAt;
    OffsetDateTime activationDate;

    public static TemplateResponse from(Templates t){
        return TemplateResponse.builder()
            .id(t.getId())
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
            .build();
    }

    private static String resolveStatus(Templates t){
        if (Boolean.TRUE.equals(t.getIsActive())) return "ACTIVE";
        if (t.getApprovedBy() != null) return "APPROVED";
        return "DRAFT";
    }

}
