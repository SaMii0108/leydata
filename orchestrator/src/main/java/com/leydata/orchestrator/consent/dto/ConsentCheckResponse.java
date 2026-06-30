package com.leydata.orchestrator.consent.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ConsentCheckResponse(
        String subjectId,
        UUID purposeId,
        String status,          // ALLOWED | REVOKED | DENIED | PENDING
        String legalBasisCode,  // null cuando status = PENDING
        LocalDateTime validUntil // expiresAt del purpose; fallback: expiration del agreement; null si no aplica
) {}
