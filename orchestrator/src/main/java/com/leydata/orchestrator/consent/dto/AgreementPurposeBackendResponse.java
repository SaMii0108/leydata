package com.leydata.orchestrator.consent.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AgreementPurposeBackendResponse(
        UUID purposeId,
        Boolean accepted,
        String status,
        String legalBasisCode,
        LocalDateTime expiresAt
) {}
