package com.leydata.orchestrator.consent.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record AgreementBackendResponse(
        UUID id,
        String status,
        LocalDateTime expiration,
        List<AgreementPurposeBackendResponse> purposes
) {}
