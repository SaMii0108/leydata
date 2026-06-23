package com.leydata.backend.privacydoc.domain.enums;

/**
 * Ciclo de vida de un Documento de Privacidad.
 *
 * Transiciones válidas:
 *   DRAFT      → IN_REVIEW
 *   IN_REVIEW  → APPROVED | REJECTED
 *   REJECTED   → IN_REVIEW   (reenvío tras corrección)
 *   APPROVED   → PUBLISHED
 *   PUBLISHED  → ARCHIVED
 */
public enum DocumentStatus {
    DRAFT,
    IN_REVIEW,
    APPROVED,
    REJECTED,
    PUBLISHED,
    ARCHIVED
}
