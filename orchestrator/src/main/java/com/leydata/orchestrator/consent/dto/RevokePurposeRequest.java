package com.leydata.orchestrator.consent.dto;

import java.util.UUID;

public record RevokePurposeRequest(
        String subjectId,
        UUID purposeId,
        String templateKey
) {}
