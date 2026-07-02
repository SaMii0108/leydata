package com.leydata.backend.template.application.dto;

import java.util.UUID;

import lombok.Builder;
import lombok.Value;

/**
 * Resolución de un template activo + su documento publicado, a partir de un identificador
 * de negocio (templateKey) y un dominio. Uso interno B2B: el Orquestador la consume para
 * que el sistema cliente (CRM/ERP) no necesite conocer UUIDs internos de antemano.
 */
@Value
@Builder
public class TemplateResolutionResponse {
    UUID templateId;
    UUID domainId;
    String templateKey;
    Integer version;
    /** Null si el template activo todavía no tiene un documento PUBLISHED asociado. */
    UUID documentId;
}
