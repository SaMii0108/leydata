package com.leydata.orchestrator.consent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LifecycleCheckBackendResponse(
        String subjectIdentifier,
        String templateKey,
        /** ALLOWED | EXPIRED | REQUIRES_RECONSENT | PENDING */
        String status,
        UUID agreementId,
        Integer agreementTemplateVersion,
        Integer currentTemplateVersion,
        LocalDateTime earliestExpiresAt
) {}
