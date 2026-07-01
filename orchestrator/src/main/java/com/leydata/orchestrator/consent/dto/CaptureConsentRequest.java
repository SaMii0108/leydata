package com.leydata.orchestrator.consent.dto;

import java.util.List;
import java.util.UUID;

public record CaptureConsentRequest(
        String subjectId,
        // Identificador de negocio del template (ej: "ONBOARDING_CLIENTE"), no el UUID interno.
        // El Orquestador lo resuelve a templateId real contra el dominio del JWT del cliente.
        String templateKey,
        // Opcional: override explícito. Si no se envía, se resuelve junto con templateKey.
        UUID documentId,
        List<PurposeDecision> purposes
) {
    public record PurposeDecision(UUID purposeId, boolean accepted) {}
}
