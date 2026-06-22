-- ==========================================================================================
-- TABLA: TEMPLATES
-- Catálogo de plantillas de consentimiento configurables por el equipo de privacidad
-- Cada template define la apariencia visual y el contenido del banner o formulario
-- Se permiten múltiples versiones por CODE, pero solo una puede estar activa a la vez
-- La activación de una nueva versión debe desactivar la anterior del mismo CODE
-- ==========================================================================================
CREATE TABLE TEMPLATES (
    ID                   UUID         PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    TEMPLATE_KEY         VARCHAR(100) NOT NULL
                             CHECK (TEMPLATE_KEY = UPPER(TEMPLATE_KEY)),
    VERSION              INTEGER      NOT NULL DEFAULT 1,
    NAME                 VARCHAR(200) NOT NULL,
    DESCRIPTION          TEXT,
    TITLE                VARCHAR(200),
    IS_ACTIVE            BOOLEAN      DEFAULT FALSE,
    CHANGE_REASON        TEXT,
    CREATED_BY           UUID,
    CREATED_AT           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    APPROVED_BY          UUID,
    APPROVED_AT          TIMESTAMPTZ,
    ACTIVATION_DATE      TIMESTAMPTZ,
    HASH_SHA256          VARCHAR(64),
    PREVIOUS_HASH_SHA256 VARCHAR(64),
    CONSTRAINT UQ_TEMPLATE_TEMPLATE_KEY_VERSION UNIQUE (TEMPLATE_KEY, VERSION)
);

ALTER TABLE TEMPLATES ADD CONSTRAINT FK_TEMPLATE_CREATED_BY
    FOREIGN KEY (CREATED_BY) REFERENCES USERS(ID) ON DELETE SET NULL;

ALTER TABLE TEMPLATES ADD CONSTRAINT FK_TEMPLATE_APPROVED_BY
    FOREIGN KEY (APPROVED_BY) REFERENCES USERS(ID) ON DELETE SET NULL;

CREATE UNIQUE INDEX UX_TEMPLATE_TEMPLATE_KEY_ACTIVE
    ON TEMPLATES (TEMPLATE_KEY)
    WHERE IS_ACTIVE = TRUE;	