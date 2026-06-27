package com.leydata.backend.purpose.application.dto;

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

    private UUID domainId;
    private String domainName;

    private String requesterId;
    private String requesterName;

    private String status; // PENDING, APPROVED, REJECTED

    private String reviewerId;
    private String reviewerName;
    private String reviewNotes; // obligatorio si REJECTED (Ley 21.719)

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
