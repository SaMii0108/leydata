package com.leydata.backend.privacydoc.domain.enums;

/**
 * Categorías fijas de Documento de Privacidad según artículos de la Ley 21.719.
 * Cada combinación (domain_id, category) admite como máximo 1 documento PUBLISHED.
 */
public enum DocumentCategory {
    POLITICA_PRIVACIDAD,     // Art. 12 — tratamiento general
    AVISO_COOKIES,           // Art. 12 — cookies y rastreo
    DATOS_SENSIBLES,         // Art. 13 — salud, biométricos, origen racial
    MARKETING_DIRECTO,       // Art. 12 — comunicaciones comerciales
    MENORES_EDAD,            // Art. 14 — menores de 14 años
    TRANSFERENCIA_TERCEROS   // Art. 16 — cesión a terceros
}
