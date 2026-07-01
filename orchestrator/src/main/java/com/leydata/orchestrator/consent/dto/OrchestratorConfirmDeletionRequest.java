package com.leydata.orchestrator.consent.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record OrchestratorConfirmDeletionRequest(
        String subjectId,
        UUID purposeId,
        LocalDateTime deletedAt
) {}
