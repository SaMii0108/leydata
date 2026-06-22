-- ==========================================================================================
-- TABLA: DATA_RETENTION_POLICIES
-- Define el periodo de retención permitido para cada combinación finalidad-categoría de dato
-- Incluye justificación legal que ampara el plazo definido (ej: normativa sectorial)
-- Si ANONYMIZE_AFTER es TRUE, los datos se anonimizan al vencer el período en lugar de eliminarse
-- Solo las políticas activas son consideradas por el motor de retención automatizado
-- ==========================================================================================
CREATE TABLE DATA_RETENTION_POLICIES (
    ID                        UUID        PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    PURPOSE_DATA_CATEGORY_ID  UUID        NOT NULL,
    VERSION                   INTEGER     NOT NULL DEFAULT 1,
    RETENTION_PERIOD          INTEGER     NOT NULL,
    RETENTION_UNIT            VARCHAR(20) NOT NULL,
    LEGAL_JUSTIFICATION       TEXT        NOT NULL,
    ANONYMIZE_AFTER           BOOLEAN     NOT NULL DEFAULT FALSE,
    IS_ACTIVE                 BOOLEAN     NOT NULL DEFAULT FALSE,
    CHANGE_REASON             TEXT,
    CREATED_AT                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    APPROVED_BY               UUID,
    APPROVED_AT               TIMESTAMPTZ,
    HASH_SHA256               VARCHAR(64),
    PREVIOUS_HASH_SHA256      VARCHAR(64),
    CONSTRAINT UQ_DRP_PDC_VERSION UNIQUE (PURPOSE_DATA_CATEGORY_ID, VERSION)
);

ALTER TABLE DATA_RETENTION_POLICIES ADD CONSTRAINT FK_RETENTION_PDC
    FOREIGN KEY (PURPOSE_DATA_CATEGORY_ID)
    REFERENCES PURPOSE_DATA_CATEGORIES(ID) ON DELETE CASCADE;

CREATE UNIQUE INDEX UX_DRP_ACTIVE_PER_PDC
    ON DATA_RETENTION_POLICIES (PURPOSE_DATA_CATEGORY_ID)
    WHERE IS_ACTIVE = TRUE;