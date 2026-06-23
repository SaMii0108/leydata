package com.leydata.backend.purposes.application.dto;

import com.leydata.backend.entity.Purposes;

import java.time.LocalDateTime;
import java.util.UUID;

public record PurposeResponse(
        UUID id,
        String code,
        String name,
        String description,
        String shortDescription,
        String consentStatement,
        Boolean required,
        Boolean revocable,
        Integer presentationOrder,
        UUID legalBasisId,
        String legalBasisCode,
        String legalBasisName,
        UUID domainId,
        String domainName,
        Boolean isActive,
        Boolean locked,
        UUID createdBy,
        UUID approvedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String hashSha256
) {
    public static PurposeResponse from(Purposes p, boolean locked) {
        return new PurposeResponse(
                p.getId(),
                p.getCode(),
                p.getName(),
                p.getDescription(),
                p.getShortDescription(),
                p.getConsentStatement(),
                p.getRequired(),
                p.getRevocable(),
                p.getPresentationOrder(),
                p.getLegalBasisId(),
                p.getLegalBasis() != null ? p.getLegalBasis().getCode() : null,
                p.getLegalBasis() != null ? p.getLegalBasis().getName() : null,
                p.getDomainId(),
                p.getDomain() != null ? p.getDomain().getName() : null,
                p.getIsActive(),
                locked,
                p.getCreatedBy(),
                p.getApprovedBy(),
                p.getCreatedAt(),
                p.getUpdatedAt(),
                p.getHashSha256()
        );
    }
}
