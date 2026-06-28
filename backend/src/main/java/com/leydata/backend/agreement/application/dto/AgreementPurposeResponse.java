package com.leydata.backend.agreement.application.dto;

import com.leydata.backend.entity.AgreementsPurposes;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class AgreementPurposeResponse {
    UUID id;
    UUID purposeId;
    Boolean accepted;
    String purposeCode;
    String purposeName;
    String purposeDescription;
    String purposeShortDescription;
    Boolean purposeRequired;
    Boolean purposeRevocable;
    String legalBasisCode;
    String status;
    LocalDateTime expiresAt;
    LocalDateTime createdAt;

    public static AgreementPurposeResponse from(AgreementsPurposes ap) {
        return AgreementPurposeResponse.builder()
            .id(ap.getId())
            .purposeId(ap.getPurposeId())
            .accepted(ap.getAccepted())
            .purposeCode(ap.getPurposeCode())
            .purposeName(ap.getPurposeName())
            .purposeDescription(ap.getPurposeDescription())
            .purposeShortDescription(ap.getPurposeShortDescription())
            .purposeRequired(ap.getPurposeRequired())
            .purposeRevocable(ap.getPurposeRevocable())
            .legalBasisCode(ap.getLegalBasisCode())
            .status(ap.getStatus())
            .expiresAt(ap.getExpiresAt())
            .createdAt(ap.getCreatedAt())
            .build();
    }
}
