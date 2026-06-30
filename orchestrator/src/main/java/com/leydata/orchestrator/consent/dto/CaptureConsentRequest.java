package com.leydata.orchestrator.consent.dto;

import java.util.List;
import java.util.UUID;

public record CaptureConsentRequest(
        String subjectId,
        UUID templateId,
        UUID documentId,
        List<PurposeDecision> purposes
) {
    public record PurposeDecision(UUID purposeId, boolean accepted) {}
}
