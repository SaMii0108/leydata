package com.leydata.backend.agreement.application.dto;

import com.leydata.backend.entity.Agreements;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Value
@Builder
public class AgreementResponse {
    UUID id;
    UUID dataSubjectId;
    UUID templateId;
    Integer templateVersion;
    UUID documentId;
    String status;
    UUID previousAgreementsId;
    LocalDateTime expiration;
    LocalDateTime createdAt;
    String hashSha256;
    List<AgreementPurposeResponse> purposes;
    AgreementMetadataResponse metadata;

    public static AgreementResponse from(Agreements a, List<AgreementPurposeResponse> purposes, AgreementMetadataResponse metadata) {
        return AgreementResponse.builder()
            .id(a.getId())
            .dataSubjectId(a.getDataSubjectId())
            .templateId(a.getTemplateId())
            .templateVersion(a.getTemplateVersion())
            .documentId(a.getDocumentId())
            .status(a.getStatus())
            .previousAgreementsId(a.getPreviousAgreementsId())
            .expiration(a.getExpiration())
            .createdAt(a.getCreatedAt())
            .hashSha256(a.getHashSha256())
            .purposes(purposes)
            .metadata(metadata)
            .build();
    }
}
