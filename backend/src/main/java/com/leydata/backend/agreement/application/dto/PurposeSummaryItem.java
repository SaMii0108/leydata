package com.leydata.backend.agreement.application.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class PurposeSummaryItem {
    UUID purposeId;
    String purposeCode;
    String purposeName;
    boolean accepted;
    /** ALLOWED | EXPIRED | REVOKED */
    String status;
    LocalDateTime expiresAt;
    LocalDateTime acceptedAt;
    boolean required;
    boolean revocable;
}
