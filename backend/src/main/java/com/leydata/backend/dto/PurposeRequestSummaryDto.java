package com.leydata.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PurposeRequestSummaryDto {
    private UUID id;
    private String title;
    private String justification;
    private String requestedData;

    // Dominio
    private UUID domainId;
    private String domainName; // nombre legible del dominio

    // Quien solicitó
    private UUID requesterId;
    private String requesterName; // nombre del JEFE_DOMINIO

    // Estado
    private String status; // PENDING, APPROVED, REJECTED

    // Quien revisó null si aún no fue revisada
    private UUID reviewerId;
    private String reviewerName; // nombre del DPO
    private String reviewNotes; // obligatorio si REJECTED

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}