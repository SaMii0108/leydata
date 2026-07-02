package com.leydata.orchestrator.consent.dto;

import java.util.UUID;

public record ConsentStatusResponse(
        String subjectId,
        UUID agreementId,
        String status  // ALLOWED | REVOKED
) {}
