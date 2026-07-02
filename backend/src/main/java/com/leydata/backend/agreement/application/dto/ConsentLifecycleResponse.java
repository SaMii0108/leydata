package com.leydata.backend.agreement.application.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Respuesta del endpoint de ciclo de vida que el Orquestador usa en el CHECK.
 * Estado:
 *   ALLOWED           — acuerdo activo y vigente
 *   EXPIRED           — expiresAt de alguna purpose ya pasó
 *   REQUIRES_RECONSENT— hay una versión más reciente del template con forceReconsent=true
 *   PENDING           — no existe acuerdo para este titular/template
 */
@Value
@Builder
public class ConsentLifecycleResponse {
    String subjectIdentifier;
    String templateKey;
    /** ALLOWED | EXPIRED | REQUIRES_RECONSENT | PENDING */
    String status;
    UUID agreementId;
    Integer agreementTemplateVersion;
    Integer currentTemplateVersion;
    LocalDateTime earliestExpiresAt;
}
