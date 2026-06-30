package com.leydata.orchestrator.consent.dto;

import java.util.UUID;

public record RevokeConsentRequest(
        String subjectId,
        UUID agreementId
) {}
