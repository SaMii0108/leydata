package com.leydata.backend.privacydoc.application.dto;

import com.leydata.backend.entity.PrivacyDocuments;
import com.leydata.backend.privacydoc.domain.enums.DocumentCategory;
import com.leydata.backend.privacydoc.domain.enums.DocumentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PrivacyDocumentResponse {

    private UUID id;
    private UUID documentFamilyId;
    private UUID templateId;
    private DocumentCategory category;
    private DocumentStatus status;
    private Integer version;
    private String name;
    private String content;
    private String rejectionReason;
    /** true si el documento tiene PDF generado (PUBLISHED o ARCHIVED). El binario se descarga por GET /{id}/pdf. */
    private boolean hasPdf;
    private String hashSha256;
    private LocalDateTime publishAt;
    private String createdBy;
    private String approvedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<UUID> purposeIds;
    private Boolean isActive;

    public static PrivacyDocumentResponse from(PrivacyDocuments e) {
        return PrivacyDocumentResponse.builder()
                .id(e.getId())
                .documentFamilyId(e.getDocumentFamilyId())
                .templateId(e.getTemplateId())
                .category(e.getCategory())
                .status(e.getStatus())
                .version(e.getVersion())
                .name(e.getName())
                .content(e.getContent())
                .rejectionReason(e.getRejectionReason())
                .hasPdf(e.getPdfContent() != null && e.getPdfContent().length > 0)
                .hashSha256(e.getHashSha256())
                .publishAt(e.getPublishAt())
                .createdBy(e.getCreatedBy())
                .approvedBy(e.getApprovedBy())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isActive(e.getIsActive())
                .purposeIds(
                        e.getDocumentPurposes().stream()
                                .filter(dp -> Boolean.TRUE.equals(dp.getIsActive()))
                                .map(dp -> dp.getId().getPurposeId())
                                .toList()
                )
                .build();
    }

    /** Versión sin content ni rejectionReason, para usuarios no privilegiados (no DPO/ADMIN). */
    public static PrivacyDocumentResponse fromPublic(PrivacyDocuments e) {
        return PrivacyDocumentResponse.builder()
                .id(e.getId())
                .documentFamilyId(e.getDocumentFamilyId())
                .templateId(e.getTemplateId())
                .category(e.getCategory())
                .status(e.getStatus())
                .version(e.getVersion())
                .name(e.getName())
                .hasPdf(e.getPdfContent() != null && e.getPdfContent().length > 0)
                .hashSha256(e.getHashSha256())
                .publishAt(e.getPublishAt())
                .createdBy(e.getCreatedBy())
                .approvedBy(e.getApprovedBy())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .isActive(e.getIsActive())
                .purposeIds(
                        e.getDocumentPurposes().stream()
                                .filter(dp -> Boolean.TRUE.equals(dp.getIsActive()))
                                .map(dp -> dp.getId().getPurposeId())
                                .toList()
                )
                .build();
    }
}
