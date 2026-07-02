package com.leydata.orchestrator.consent.dto;

import java.util.UUID;

public record TemplateResolutionResponse(
        UUID templateId,
        UUID domainId,
        String templateKey,
        Integer version,
        UUID documentId
) {}
