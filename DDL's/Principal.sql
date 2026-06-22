-- ==========================================================================================
-- TABLA: USERS
-- Registro de usuarios del sistema con acceso al módulo
-- Almacena identidad, estado de activación y fecha de incorporación
-- Todo usuario debe tener al menos un rol asignado para operar en el sistema
-- ==========================================================================================
CREATE TABLE USERS(
	ID UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
	EMAIL VARCHAR(255) NOT NULL UNIQUE,
	NAME VARCHAR(200) NOT NULL,
	PASSWORD VARCHAR(256) NOT NULL,
	IS_ACTIVE BOOLEAN NOT NULL DEFAULT TRUE,
	IS_BLOCKED BOOLEAN NOT NULL DEFAULT FALSE,
	CREATED_AT TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- AGREGAR REGLA SI EL USARIO ESTA BLOQUEADO (DESPEDIDO) NO PUEDE ESTAR ACTIVO (EL IS ACTIVE ES TEMPORAL)

-- ==========================================================================================
-- TABLA: ROLE
-- Catálogo de roles disponibles en el sistema (DPO, LEGAL, ADMIN, JEFE_DOMINIO)
-- Define los perfiles de acceso y responsabilidad dentro del módulo
-- Los roles son asignados por un ADMIN y determinan los permisos operativos del usuario
-- ==========================================================================================
CREATE TABLE ROLE(
	ID SERIAL PRIMARY KEY,
	CODE VARCHAR(50) NOT NULL UNIQUE, --DPO, LEGAL, ADMIN, JEFE_DOMINIO
	NAME VARCHAR(100) NOT NULL
);

-- ==========================================================================================
-- TABLA: USER_ROLE
-- Relación muchos a muchos entre usuarios y roles
-- Un usuario puede tener múltiples roles; un rol puede esta asignado a múltiples usuarios
-- La combinación (USER_ID, ROLE_ID es única, no se permiten asignaciones duplicadas 
-- ==========================================================================================
CREATE TABLE USERS_ROLE(
	USER_ID UUID NOT NULL,
	ROLE_ID INTEGER NOT NULL,
	PRIMARY KEY (USER_ID, ROLE_ID)
);

ALTER TABLE USERS_ROLE ADD CONSTRAINT FK_USERS_ROLE_USER
FOREIGN KEY (USER_ID) REFERENCES USERS(ID) ON DELETE CASCADE;

ALTER TABLE USERS_ROLE ADD CONSTRAINT FK_USERS_ROLE_ROLE
FOREIGN KEY (ROLE_ID) REFERENCES ROLE(ID) ON DELETE RESTRICT; 

-- ==========================================================================================
-- TABLA: DOMAINS
-- Catálogo de dominios de datos dentro de la organización (ej: RRHH, Finanzas, Marketing)
-- Cada dominio agrupa activos de datos bajo la responsabilidad de un JEFE_DOMINIO
-- El CODE actúa como slug único para identificación en APIs externas
-- ==========================================================================================
CREATE TABLE DOMAINS(
	ID UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
	CODE VARCHAR(50) NOT NULL UNIQUE, -- Slug único, Id para APIs externas
	NAME VARCHAR(100) NOT NULL,
	DESCRIPTION TEXT
);

-- ==========================================================================================
-- TABLA USER_DOMAINS
-- Relación muchos a muchos entre usuarios y dominios
-- Determina qué usuarios tienen responsabilidad o acceso sobre cada dominio de datos
-- La eliminación de un usuario o dominio elimina en cascada la asignación
-- ==========================================================================================
CREATE TABLE USER_DOMAINS(
	USER_ID UUID NOT NULL,
	DOMAIN_ID UUID NOT NULL,
	PRIMARY KEY (USER_ID, DOMAIN_ID) 
);

ALTER TABLE USER_DOMAINS ADD CONSTRAINT FK_USER_DOMAINS_USER 
FOREIGN KEY (USER_ID) REFERENCES USERS(ID) ON DELETE CASCADE;

ALTER TABLE USER_DOMAINS ADD CONSTRAINT FK_USER_DOMAINS_DOMAIN
FOREIGN KEY (DOMAIN_ID) REFERENCES DOMAINS(ID) ON DELETE CASCADE;

-- ==========================================================================================
-- TABLA: LEGAL_BASIS_CATALOG
-- Catálogo de bases legales según Art. 13 Ley 21.719 
-- ==========================================================================================
CREATE TABLE LEGAL_BASIS_CATALOG (
	ID UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
	CODE VARCHAR(50) NOT NULL UNIQUE, -- consent | legitimate_interest | legal_obligation | vital_interest | public_interest
	NAME VARCHAR(200) NOT NULL, -- Consentimiento | Interés legítimo | obligación legal | Interés vital | Interés público
	DESCRIPTION TEXT NOT NULL,
	CONSENT_REQUIRED BOOLEAN NOT NULL, -- TRUE solo para 'consent'
	IS_ACTIVE BOOLEAN NOT NULL DEFAULT TRUE  
);

-- ==========================================================================================
-- TABLA: PURPOSES
-- Catálogo de finalidades de tratamiento de datos
-- Define QUÉ se hace con los datos y con qué propósito
-- Requiere la aprobación del DPO + Legal antes de activar una nueva finalidad
-- ==========================================================================================
CREATE TABLE PURPOSES(
	ID UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
	TEMPLATE_KEY VARCHAR(50) NOT NULL,
	VERSION INTEGER NOT NULL DEFAULT 1,
	DOMAIN_ID UUID,
	NAME VARCHAR(200) NOT NULL,
	DESCRIPTION TEXT NOT NULL,
	SHORT_DESCRIPTION VARCHAR(300) NOT NULL,
	REQUIRED BOOLEAN NOT NULL DEFAULT FALSE,
	REVOCABLE BOOLEAN NOT NULL DEFAULT TRUE,
	PRESENTATION_ORDER INTEGER NOT NULL DEFAULT 0,
		CHECK (PRESENTATION_ORDER >= 0),
	IS_ACTIVE BOOLEAN NOT NULL DEFAULT FALSE,
	CHANGE_REASON TEXT,
	CREATED_BY UUID NOT NULL,
	CREATED_AT TIMESTAMPTZ NOT NULL DEFAULT NOW(),
	APPROVED_BY UUID,
	APPROVED_AT TIMESTAMPTZ,
	HASH_SHA256 VARCHAR(64),
	PREVIOUS_HASH_SHA256 VARCHAR(64),

	CONSTRAINT UQ_PURPOSE_TEMPLATE_KEY_VERSION UNIQUE (TEMPLATE_KEY, VERSION)
);

CREATE UNIQUE INDEX UX_PURPOSE_TEMPLATE_KEY_ACTIVE ON PURPOSES (TEMPLATE_KEY) WHERE IS_ACTIVE = TRUE;

ALTER TABLE PURPOSES ADD CONSTRAINT FK_PURPOSES_CREATED_BY 
FOREIGN KEY (CREATED_BY) REFERENCES USERS(ID) ON DELETE SET NUL;

ALTER TABLE PURPOSES ADD CONSTRAINT FK_PURPOSES_DOMAIN
FOREIGN KEY (DOMAIN_ID) REFERENCES DOMAINS(ID);

ALTER TALE PURPOSES ADD CONSTRAINT FK_PURPOSES_APPROVED_BY
FOREIGN KEY (APPROVED_BY) REFERENCES USERS(ID) ON DELETE SET NULL;
-- ==========================================================================================
-- TABLA:PRIVACY_DOCUMENTS
-- Políticas de privacidad, términos y condiciones concernientes a los consentimientos
-- INMUTABLE: Esta tabla nunca se modifica, solo se versiona con nuevos documentos
-- ==========================================================================================
CREATE TABLE PRIVACY_DOCUMENTS (
	ID UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
	TEMPLATE_KEY VARCHAR(100) NOT NULL, -- Único: POLITICA_PRIVACIDAD | TERMINOS Y CONDICIONES, 'Familia' del documento, 'Versión' dentro de la familia
	VERSION INTEGER NOT NULL DEFAULT 1,
	NAME VARCHAR(200) NOT NULL,
	URL_DOCUMENT VARCHAR(500) NOT NULL, -- Ruta de almacenamiento interno del documento
	HASH_SHA256 VARCHAR(64) NOT NULL, -- Integridad del documento (OBLIGATORIO),
	IS_ACTIVE BOOLEAN NOT NULL DEFAULT FALSE,
	PUBLISH_AT TIMESTAMPTZ,
	CREATED_AT TIMESTAMPTZ NOT NULL DEFAULT NOW(),
	CREATED_BY UUID,
	APPROVED_BY UUID,
	UNIQUE(TEMPLATE_KEY, VERSION),
	CHECK(TEMPLATE_KEY = UPPER(TEMPLATE_KEY))
);

CREATE UNIQUE INDEX UNIQUE_IS_ACTIVE_PER_TEMPLATE ON PRIVACY_DOCUMENTS (TEMPLATE_KEY) WHERE IS_ACTIVE = TRUE;

ALTER TABLE PRIVACY_DOCUMENTS ADD CONSTRAINT FK_DOCUMENTS_CREATED_BY
FOREIGN KEY (CREATED_BY) REFERENCES USERS(ID) ON DELETE SET NULL;

ALTER TABLE PRIVACY_DOCUMENTS ADD CONSTRAINT FK_DOCUMENTS_APPROVED_BY
FOREIGN KEY (APPROVED_BY) REFERENCES USERS(ID) ON DELETE SET NULL;

-- ==========================================================================================
-- TABLA: DOCUMENT_PURPOSES
-- Relación muchos a muchos entre documentos de privacidad y finalidades de tratamiento
-- Define que finalidades están cubiertas por cada documento
-- La eliminación de un documento o finalidad elimina en cascada la asociación
-- ==========================================================================================
CREATE TABLE DOCUMENT_PURPOSES(
	DOCUMENT_ID UUID NOT NULL,
	PURPOSE_ID UUID NOT NULL,
	PRIMARY KEY (DOCUMENT_ID, PURPOSE_ID)
);

ALTER TABLE DOCUMENT_PURPOSES ADD CONSTRAINT FK_DOC_PURPOSE_DOC
FOREIGN KEY (DOCUMENT_ID) REFERENCES PRIVACY_DOCUMENTS(ID) ON DELETE CASCADE;

ALTER TABLE DOCUMENT_PURPOSES ADD CONSTRAINT FK_DOC_PURPOSE_PURPOSE
FOREIGN KEY (PURPOSE_ID) REFERENCES PURPOSES(ID) ON DELETE CASCADE;

-- ==========================================================================================
-- TABLA: DATA_CATEGORIES
-- Catálogo de categorías de datos personales tratados por la organización
-- Distingue entre datos comunes y datos sensibles (salud, biometría, origen racial...)
-- Las categorías sencibles requieren mayor nivel de justificación y control de acceso
-- Solo las categorías activas pueden ser asociadas a nuevas finalidades
-- ==========================================================================================
CREATE TABLE DATA_CATEGORIES(
	ID UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
	CODE VARCHAR(50) NOT NULL UNIQUE, -- Email, rut, salud, location
	NAME VARCHAR(150) NOT NULL,
	DESCRIPTION TEXT,
	IS_SENSITIVE BOOLEAN NOT NULL DEFAULT FALSE, -- Datos sensibles
	IS_ACTIVE BOOLEAN NOT NULL DEFAULT TRUE
);

-- ==========================================================================================
-- TABLA: PURPOSE_DATA_CATEGORIES
-- Relación muchos a muchos entre finalidades y categorías de datos
-- Especifica qué categorías de datos son necesarias para cumplir cada finalidad
-- REQUIRED indica si la categoría es obligatoria o solo complementaria para esa finalidad
-- La eliminación de una categoría está restringida si está en uso por alguna finalidad
-- ==========================================================================================
CREATE TABLE PURPOSE_DATA_CATEGORIES(
	ID UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
	PURPOSE_ID UUID NOT NULL,
	DATA_CATEGORY_ID UUID NOT NULL,
	REQUIRED BOOLEAN NOT NULL DEFAULT TRUE, --Si es necesario para la finalidad
	UNIQUE (PURPOSE_ID, DATA_CATEGORY_ID)
);

ALTER TABLE PURPOSE_DATA_CATEGORIES ADD CONSTRAINT FK_PDC_PURPOSE
FOREIGN KEY (PURPOSE_ID) REFERENCES PURPOSES(ID) ON DELETE CASCADE;

ALTER TABLE PURPOSE_DATA_CATEGORIES ADD CONSTRAINT FK_PDC_CATEGORY
FOREIGN KEY (DATA_CATEGORY_ID) REFERENCES DATA_CATEGORIES(ID) ON DELETE RESTRICT;

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
    PRIMARY_COLOR        VARCHAR(7),
    ACCENT_COLOR         VARCHAR(7),
    BACKGROUND_COLOR     VARCHAR(7),
    TITLE                VARCHAR(200),
    BUTTON_ACCEPT_TEXT   VARCHAR(100),
    BUTTON_REJECT_TEXT   VARCHAR(100),
    LEGAL_BASIS_ID       UUID         NOT NULL,
    IS_ACTIVE            BOOLEAN      DEFAULT FALSE,
    CHANGE_REASON        TEXT,
    CREATED_BY           UUID,
    CREATED_AT           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    APPROVED_BY          UUID,
    APPROVED_AT          TIMESTAMPTZ,
    HASH_SHA256          VARCHAR(64),
    PREVIOUS_HASH_SHA256 VARCHAR(64),
    CONSTRAINT UQ_TEMPLATE_TEMPLATE_KEY_VERSION UNIQUE (TEMPLATE_KEY, VERSION)
);

ALTER TABLE TEMPLATES ADD CONSTRAINT FK_TEMPLATE_LEGAL_BASIS
    FOREIGN KEY (LEGAL_BASIS_ID) REFERENCES LEGAL_BASIS_CATALOG(ID);

ALTER TABLE TEMPLATES ADD CONSTRAINT FK_TEMPLATE_CREATED_BY
    FOREIGN KEY (CREATED_BY) REFERENCES USERS(ID) ON DELETE SET NULL;

ALTER TABLE TEMPLATES ADD CONSTRAINT FK_TEMPLATE_APPROVED_BY
    FOREIGN KEY (APPROVED_BY) REFERENCES USERS(ID) ON DELETE SET NULL;

CREATE UNIQUE INDEX UX_TEMPLATE_TEMPLATE_KEY_ACTIVE
    ON TEMPLATES (TEMPLATE_KEY)
    WHERE IS_ACTIVE = TRUE;	
-- ==========================================================================================
-- TABLA TEMPLATE_PURPOSES
-- Relación muchos a muchos entre templates y finalidades de tratamiento
-- Define que finalidades se presentan en cada platilla y en qué orden visual
-- IS_VISIBLE permite ocultar una finalidad sin eliminarla del template
-- La eliminación de un template elimina en cascada todas sus asociaciones de finalidades
-- ==========================================================================================
CREATE TABLE TEMPLATE_PURPOSES(
	TEMPLATE_ID UUID NOT NULL,
	PURPOSE_ID UUID NOT NULL,
	ORDER_POSITION INTEGER NOT NULL DEFAULT 0,
	IS_VISIBLE BOOLEAN NOT NULL DEFAULT TRUE,
	PRIMARY KEY (TEMPLATE_ID, PURPOSE_ID)
);

ALTER TABLE TEMPLATE_PURPOSES ADD CONSTRAINT FK_TP_TEMPLATE
FOREIGN KEY (TEMPLATE_ID) REFERENCES TEMPLATES(ID) ON DELETE CASCADE;

ALTER TABLE TEMPLATE_PURPOSES ADD CONSTRAINT FK_TP_PURPOSE
FOREIGN KEY (PURPOSE_ID) REFERENCES PURPOSES(ID) ON DELETE CASCADE;

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
-- ==========================================================================================
-- TABLA: DATA_SUBJECTS
-- Representa a los titulares de datos personales en el sistema
-- El IDENTIFIER es un valor opaco o sedonimizado que no expone la identidad directamente
-- Se usa como ancla para vincular acuerdos de consentimiento sin almacenar PII en esta tabla
-- ==========================================================================================
CREATE TABLE DATA_SUBJECTS(
	ID UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
	IDENTIFIER VARCHAR(255) NOT NULL, -- Email u otro identificador existente
	CREATED_AT TIMESTAMPTZ DEFAULT NOW()
);

-- ==========================================================================================
-- TABLA: AGREEMENTS
-- Registro inmutable de cada acto de consentimiento otorgado por un titular de datos
-- Captura el consentimiento técnico (IP, user agent) y el documento legal vigente al momento 
-- PREVIOUS_AGREEMENTS_ID permite trazar la cadena de renovaciones o modificaciones
-- STATUS refleja el ciclo de vida del acuerdo: activo, revocado o expirado
-- ==========================================================================================
CREATE TABLE AGREEMENTS (
    ID                    UUID        PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    DATA_SUBJECT_ID       UUID        NOT NULL,
    TEMPLATE_ID           UUID        NOT NULL,
    TEMPLATE_VERSION      INTEGER     NOT NULL,
    DOCUMENT_ID           UUID        NOT NULL,
    STATUS                VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                              CHECK (STATUS IN ('ACTIVE', 'REVOKED', 'EXPIRED')),
    IP_ORIGIN             INET,
    USER_AGENT            TEXT,
    EXPIRATION            TIMESTAMPTZ,
    PREVIOUS_AGREEMENTS_ID UUID,
    CREATED_AT            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    HASH_SHA256           VARCHAR(64),
    PREVIOUS_HASH_SHA256  VARCHAR(64)
);

ALTER TABLE AGREEMENTS ADD CONSTRAINT FK_AGREEMENT_SUBJECT
    FOREIGN KEY (DATA_SUBJECT_ID) REFERENCES DATA_SUBJECTS(ID) ON DELETE CASCADE;

ALTER TABLE AGREEMENTS ADD CONSTRAINT FK_AGREEMENT_TEMPLATE
    FOREIGN KEY (TEMPLATE_ID) REFERENCES TEMPLATES(ID) ON DELETE RESTRICT;

ALTER TABLE AGREEMENTS ADD CONSTRAINT FK_AGREEMENT_DOCUMENT
    FOREIGN KEY (DOCUMENT_ID) REFERENCES PRIVACY_DOCUMENTS(ID) ON DELETE RESTRICT;

ALTER TABLE AGREEMENTS ADD CONSTRAINT FK_AGREEMENTS_PREVIOUS
    FOREIGN KEY (PREVIOUS_AGREEMENTS_ID) REFERENCES AGREEMENTS(ID) ON DELETE SET NULL;

CREATE INDEX IDX_AGREEMENTS_SUBJECT    ON AGREEMENTS (DATA_SUBJECT_ID);
CREATE INDEX IDX_AGREEMENTS_PREVIOUS   ON AGREEMENTS (PREVIOUS_AGREEMENTS_ID);
CREATE INDEX IDX_AGREEMENTS_STATUS_EXPIRATION ON AGREEMENTS (STATUS, EXPIRATION);
-- ==========================================================================================
-- TABLA: AGREEMENTS_PURPOSES
-- Detalle inmutable de cada finalidad aceptada o rechazada dentro de un acuerdo
-- Snapshot de los atributos de la finalidad al momento del consentimiento (nombre, hash...) 
-- PURPOSE-HASH permite verificar que la finalidad no fue alterada post-consentimiento
-- La combinación (AGREEMENT_ID, PURPOSE_ID) es única dentro de un mismo acuerdo
-- ==========================================================================================
CREATE TABLE AGREEMENTS_PURPOSES (
	ID UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
	AGREEMENT_ID UUID NOT NULL,
	PURPOSE_ID UUID NOT NULL, 
	ACCEPTED BOOLEAN NOT NULL,

	PURPOSE_CODE VARCHAR(50) NOT NULL,
	PURPOSE_NAME VARCHAR(200) NOT NULL,
	PURPOSE_DESCRIPTION TEXT NOT NULL,
	PURPOSE_SHORT_DESCRIPTION VARCHAR(300) NOT NULL,

	PURPOSE_REQUIRED BOOLEAN NOT NULL,
		CHECK (NOT (PURPOSE_REQUIRED = TRUE AND ACCEPTED = FALSE)),
	PURPOSE_REVOCABLE BOOLEAN NOT NULL,

	PURPOSE_HASH VARCHAR(64) NOT NULL,

	LEGAL_BASIS_CODE VARCHAR(50) NOT NULL,

	CREATED_AT TIMESTAMPTZ NOT NULL DEFAULT NOW(),

	HASH_SHA256 VARCHAR(64),

	UNIQUE (AGREEMENT_ID, PURPOSE_ID)
);

ALTER TABLE AGREEMENTS_PURPOSES ADD CONSTRAINT FK_AP_AGREEMENT
FOREIGN KEY (AGREEMENT_ID) REFERENCES AGREEMENTS(ID) ON DELETE CASCADE;

-- Consultas por finalidad
CREATE INDEX IDX_AP_PURPOSE ON AGREEMENTS_PURPOSES(PURPOSE_ID);

-- Consultas por acuerdo
CREATE INDEX IDX_AP_AGREEMENT ON AGREEMENTS_PURPOSES(AGREEMENT_ID);

-- ==========================================================================================
-- TABLA: PURPOSE_REQUEST
-- Flujo de solicitud y aprobación para incorporar nuevas finalidad de tratamiento
-- Toda finalidad de tratamiento debe ser solicitada por el responsable de dominio y aprobada por el DPO/legal
-- PURPOSE_ID se llena solo al ser aprobada la solicitud, vinculando la finalidad creada
-- REVIEWED_AT permanece null hasta que el revisor actúe sobre la solicitud
-- ==========================================================================================
CREATE TABLE PURPOSE_REQUEST(
	ID UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
	DOMAIN_ID UUID NOT NULL,
	REQUESTER_ID UUID NOT NULL,
	NAME VARCHAR(200) NOT NULL,
	JUSTIFICATION TEXT NOT NULL,
	STATUS VARCHAR(20) NOT NULL DEFAULT 'PENDING'
		CHECK (STATUS IN ('PENDING', 'IN_REVIEW','APPROVED', 'REJECTED')),
	REVIEW_NOTES TEXT,
	REVIEWED_BY UUID,
	PURPOSE_ID UUID,
	REVIEWED_AT TIMESTAMPTZ,
	CREATED_AT TIMESTAMPTZ NOT NULL DEFAULT NOW(),
	UPDATED_AT TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE PURPOSE_REQUEST ADD CONSTRAINT FK_PR_DOMAIN
FOREIGN KEY (DOMAIN_ID) REFERENCES DOMAINS(ID) ON DELETE RESTRICT;

ALTER TABLE PURPOSE_REQUEST ADD CONSTRAINT FK_PR_REQUESTER
FOREIGN KEY (REQUESTER_ID) REFERENCES USERS(ID) ON DELETE RESTRICT;

ALTER TABLE PURPOSE_REQUEST ADD CONSTRAINT FK_PR_REVIEWER
FOREIGN KEY (REVIEWED_BY) REFERENCES USERS(ID) ON DELETE SET NULL;

ALTER TABLE PURPOSE_REQUEST ADD CONSTRAINT FK_PR_PURPOSE
FOREIGN KEY (PURPOSE_ID) REFERENCES PURPOSES(ID) ON DELETE SET NULL;

-- ==========================================================================================
-- TABLA: AGREEMENT_INTEGRITY_LOG
-- No guarda datos, guarda la evidencia que verificamos esos datos
-- Registra cada verificación de integridad realizada sobre un acuerdo de consentimiento 
-- IS_VALID FALSE indica una posible alteración que debe ser investigada
-- CHECK_TYPE distingue entre verificaciones programadas, manuales o bajo demanda
-- ==========================================================================================
CREATE TABLE AGREEMENT_INTEGRITY_LOG (
	ID UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
	AGREEMENT_ID UUID NOT NULL,
	STORED_HASH VARCHAR(64) NOT NULL,
	RECALCULATED_HASH VARCHAR(64) NOT NULL,
	IS_VALID BOOLEAN NOT NULL,
	CHECK_TYPE VARCHAR(20) NOT NULL,
		CHECK (CHECK_TYPE IN ('SCHEDULED', 'MANUAL', 'ON_DEMAND')),
	CREATED_AT TIMESTAMPTZ NOT NULL DEFAULT NOW(),
	ERROR_DETAIL TEXT, 
	CREATED_BY UUID -- Opcional si es manual
	HASH_SHA256 VARCHAR(64),
	PREVIOUS_HASH_SHA256_ID VARCHAR(64)
);

ALTER TABLE AGREEMENT_INTEGRITY_LOG ADD CONSTRAINT FK_AIL_AGREEMENT
FOREIGN KEY (AGREEMENT_ID) REFERENCES AGREEMENTS(ID) ON DELETE CASCADE;

ALTER TABLE AGREEMENT_INTEGRITY_LOG ADD CONSTRAINT FK_AIL_PREVIOUS_HASH
FOREIGN KEY (PREVIOUS_HASH_SHA256) REFERENCES(HASH_SHA256);

-- Índice Ail - Agreement 
CREATE INDEX IDX_AIL_AGREEMENTS ON AGREEMENT_INTEGRITY_LOG(AGREEMENT_ID);

-- Filtro rápido de verificaciones fallidas
CREATE INDEX IDX_AIL_INVALID ON AGREEMENT_INTEGRITY_LOG(IS_VALID) WHERE IS_VALID = FALSE;

-- ==========================================================================================
-- TABLA: ENTITY_INTEGRITY_LOG
-- Registro de verificaciones de integridad sobre entidades maestras del sistema
-- Cubre finalidades, documentos, dominios y políticas de retención
-- Permite detectar alteraciones fuera del flujo normal de la aplicación
-- CREATED_BY es opcional: NULL en verificaciones automáticas, poblado en manuales
-- ==========================================================================================
CREATE TABLE ENTITY_INTEGRITY_LOG(
	ID UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
	ENTITY_TYPE VARCHAR(50) NOT NULL, -- Purpose, Document, Domain, Retention_policy
		CHECK (ENTITY_TYPE IN ('PURPOSE', 'DOCUMENT', 'DOMAIN', 'RETENTION_POLICY')),
	ENTITY_ID UUID NOT NULL,
	STORED_HASH VARCHAR(64) NOT NULL,
	RECALCULATED_HASH VARCHAR(64) NOT NULL,
	IS_VALID BOOLEAN NOT NULL,
	CHECK_TYPE VARCHAR(20) NOT NULL
		CHECK(CHECK_TYPE IN ('SCHEDULED', 'MANUAL', 'ON_DEMAND')),
	CHECKED_AT TIMESTAMPTZ NOT NULL DEFAULT NOW(),
	ERROR_DETAIL TEXT,
	CREATED_BY UUID,
	HASH_SHA256 VARCHAR(64),
	PREVIOUS_HASH_SHA256_ID VARCHAR(64)
);

ALTER TABLE ENTITY_INTEGRITY_LOG ADD CONSTRAINT FK_EIL_CREATED_BY
FOREIGN KEY (CREATED_BY) REFERENCES USERS(ID) ON DELETE SET NULL;

ALTER TABLE ENTITY_INTEGRITY_LOG ADD CONSTRAINT FK_EIL_PREVIOUS_HASH
FOREIGN KEY (PREVIOUS_HASH_SHA256_ID) REFERENCES ENTITY_INTEGRITY_LOG(HASH_SHA256) ON DELETE SET NULL;

CREATE INDEX IDX_EIL_ENTITY ON ENTITY_INTEGRITY_LOG(ENTITY_TYPE, ENTITY_ID);

-- ==========================================================================================
-- TABLA: AUDIT_LOG
-- Registro inmutable de cambios realizados sobre las tablas críticas del sistema
-- Captura el estado anterior y posterior de cada registro modificado en formato JSONB
-- CHANGED_BY puede ser NULL si la operación fue ejecutada por un procesos automátizado
-- No debe tener políticas de borrado: es evidencia de trazabilidad y no admisión
-- ==========================================================================================
CREATE TABLE AUDIT_LOG(
	ID UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
	TABLE_NAME VARCHAR(100),
	RECORD_ID UUID,
	ACTION VARCHAR(20), -- Insert, Update
		CHECK (ACTION IN ('INSERT', 'UPDATE', 'DELETE')),
	OLD_DATA JSONB,
	NEW_DATA JSONB,
	CHANGED_BY UUID,
	CHANGED_BY_NAME VARCHAR(200),
	CHANGED_REASON TEXT,
	CREATED_AT TIMESTAMPTZ DEFAULT NOW()
	HASH_SHA256 VARCHAR(64),
	PREVIOUS_HASH_SHA256 VARCHAR(64)
);

ALTER TABLE AUDIT_LOG ADD CONSTRAINT FK_AUDIT_USER 
FOREIGN KEY (CHANGED_BY) REFERENCES USERS(ID) ON DELETE SET NULL;

ALTER TABLE AUDIT_LOG ADD CONSTRAINT FK_AUDIT_PREVIOUS_HASH
FOREIGN KEY (PREVIOUS_HASH_SHA256) REFERENCES AUDIT_LOG(HASH_SHA256);

-- ==========================================================================================
-- TABLA: TREATMENT_RECORDS
-- Registro general del tratamiento
--
-- ==========================================================================================
CREATE TABLE TREATMENT_RECORDS (
    ID                          UUID        PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
    PURPOSE_ID                  UUID        NOT NULL,
    DATA_SUBJECT_ID             UUID,
    LEGAL_BASIS_CODE            VARCHAR(50) NOT NULL,
    LEGAL_JUSTIFICATION         TEXT        NOT NULL,
    STARTED_AT                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ENDED_AT                    TIMESTAMPTZ,
    CREATED_BY                  UUID        NOT NULL,
    TEMPLATE_ID                 UUID        NOT NULL,
    HASH_SHA256                 VARCHAR(64),
    PREVIOUS_HASH_SHA256        VARCHAR(64),
    PREVIOUS_TREATMENT_RECORDS_ID UUID
);

ALTER TABLE TREATMENT_RECORDS ADD CONSTRAINT FK_TR_PURPOSE
    FOREIGN KEY (PURPOSE_ID) REFERENCES PURPOSES(ID) ON DELETE RESTRICT;

ALTER TABLE TREATMENT_RECORDS ADD CONSTRAINT FK_TR_DATA_SUBJECT
    FOREIGN KEY (DATA_SUBJECT_ID) REFERENCES DATA_SUBJECTS(ID) ON DELETE RESTRICT;

ALTER TABLE TREATMENT_RECORDS ADD CONSTRAINT FK_TR_PREVIOUS
    FOREIGN KEY (PREVIOUS_TREATMENT_RECORDS_ID) REFERENCES TREATMENT_RECORDS(ID) ON DELETE SET NULL;

ALTER TABLE TREATMENT_RECORDS ADD CONSTRAINT FK_TR_CREATED_BY
    FOREIGN KEY (CREATED_BY) REFERENCES USERS(ID) ON DELETE RESTRICT;

ALTER TABLE TREATMENT_RECORDS ADD CONSTRAINT FK_TR_TEMPLATE
    FOREIGN KEY (TEMPLATE_ID) REFERENCES TEMPLATES(ID) ON DELETE RESTRICT;

-- ==========================================================================================
-- TABLA: PURPOSE_CHANGELOG
--
-- ==========================================================================================
CREATE TABLE PURPOSE_CHANGELOG(
	ID UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
	PURPOSE_ID UUID NOT NULL,
	PURPOSE_VERSION_ID UUID NOT NULL,
	CHANGED_BY UUID,
	CHANGE_TYPE VARCHAR(30) NOT NULL
		CHECK (CHANGE_TYPE IN ('CREATED', 'UPDATED','ACTIVATED','DEACTIVATED','APPROVED','REJECTED')),
	FIELD_NAME VARCHAR(100),
	OLD_VALUE TEXT,
	NEW_VALUE TEXT,
	CHANGE_REASON TEXT,
	CREATED_AT TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE PURPOSE_CHANGELOG ADD CONSTRAINT FK_PCL_PURPOSE
FOREIGN KEY (PURPOSE_ID) REFERENCES PURPOSES(ID) ON DELETE CASCADE;

ALTER TABLE PURPOSE_CHANGELOG ADD CONSTRAINT FK_PLC_USER
FOREIGN KEY (CHANGED_BY) REFERENCES USERS(ID) ON DELETE SET NULL;

CREATE INDEX IDX_PCL_CREATED ON PURPOSE_CHANGELOG(CREATED_AT DESC);
CREATE INDEX IDX_PCL_PURPOSE ON PURPOSE_CHANGELOG(PURPOSE_ID);

-- DE AQUÍ HACIA ABAJO SOLO ES TEORICO --
-- ==========================================================================================
-- FUNCIÓN: TRG_VALIDATE_TEMPLATE_PURPOSE_CONSENT
-- En consentimiento todas las finalidades deben poder ser rechazadas
-- (Libertad del consentimiento, Art. 13 Ley 21.719
-- ==========================================================================================
CREATE OR REPLACE FUNCTION TRG_VALIDATE_TEMPLATE_PURPOSE_CONSENT()
RETURNS TRIGGER AS $$
DECLARE
	V_CONSENT_REQUIRED BOOLEAN;
	V_PURPOSE_REQUIRED BOOLEAN;
	V_PURPOSE_REVOCABLE BOOLEAN;
BEGIN
	-- Obtenemos el template del consentimiento
	SELECT LBC.CONSENT_REQUIRED
	INTO V_CONSENT_REQUIRED 
	FROM TEMPLATES T JOIN LEGAL_BASIS_CATALOG LBC ON LBC.ID = T.LEGAL_BASIS_ID
	WHERE T.ID = NEW.TEMPLATE_ID;
	
	-- Obtenemos los atributos de la finalidad 
	SELECT REQUIRED, REVOCABLE
	INTO V_PURPOSE_REQUIRED, V_PURPOSE_REVOCABLE
	FROM PURPOSES P JOIN PURPOSES_VERSIONS PV
	ON PV.ID = P.CURRENT_VERSION_ID
	WHERE ID = NEW.PURPOSE_ID;
	
	-- REGLA 1: En consentimiento ninguna finalidad puede ser REQUIRED = TRUE
	-- (El titular puede poder rechazarla libremente)
	IF V_CONSENT_REQUIRED = TRUE AND V_PURPOSE_REQUIRED = TRUE THEN 
	RAISE EXCEPTION 
		'Una finalidad REQUIRED no puede asociarse a un template de consentimiento.
		 El titular debe poder rechazarla libremente. PURPOSE_ID: %', NEW.PURPOSE_ID;
	END IF;

	-- REGLA 2: En bases no-consentimiento, ninguna finalidad puede ser REVOCABLE = TRUE
	-- (El tratamiento ocurre por mandato legal, no por voluntad del titular)
	IF V_CONSENT_REQUIRED = FALSE AND V_PURPOSE_REVOCABLE = TRUE THEN 
	RAISE EXCEPTION 
		'Una finalidad REVOCABLE no puede asociarse a un Template de base no-consentimiento 
		 El tratamiento no depende de la voluntad del titular. PURPOSE_ID: %', NEW.PURPOSE_ID;
	END IF;
	RETURN NEW;
END;
$$ LANGUAGE PLPGSQL;

-- ==========================================================================================
-- TRIGGER: VALIDATE_TEMPLATE_BASIS_CHANGE
-- Ejecuta la función anterior
-- ==========================================================================================
DROP TRIGGER IF EXISTS VALIDATE_TEMPLATE_PURPOSE_CONSENT ON TEMPLATE_PURPOSES;

CREATE TRIGGER VALIDATE_TEMPLATE_PURPOSE_CONSENT 
	BEFORE INSERT OR UPDATE
	ON TEMPLATE_PURPOSES
	FOR EACH ROW 
	EXECUTE FUNCTION TRG_VALIDATE_TEMPLATE_PURPOSE_CONSENT();

-- ==========================================================================================
-- FUNCIÓN: TRG_VALIDATE_TEMPLATE_BASIS_CHANGE
-- Si cambia el legal basis las reglas asociadas en la función anterior pueden ser violadas
-- ==========================================================================================
CREATE OR REPLACE FUNCTION TRG_VALIDATE_TEMPLATE_BASIS_CHANGE()
RETURNS TRIGGER AS $$
DECLARE 
    V_CONSENT_REQUIRED BOOLEAN;
    V_CONFLICT_COUNT   INTEGER := 0; 
BEGIN 
    -- Solo actúa si cambia la base de licitud
    IF NEW.LEGAL_BASIS_ID = OLD.LEGAL_BASIS_ID THEN 
        RETURN NEW;
    END IF;
    
    SELECT LBC.CONSENT_REQUIRED 
    INTO V_CONSENT_REQUIRED
    FROM LEGAL_BASIS_CATALOG LBC
    WHERE LBC.ID = NEW.LEGAL_BASIS_ID;

    -- Si la nueva base es consentimiento: no puede haber finalidades REQUIRED
    IF V_CONSENT_REQUIRED = TRUE THEN

        SELECT COUNT(*) INTO V_CONFLICT_COUNT
        FROM TEMPLATE_PURPOSES TP
        JOIN PURPOSES P   ON P.ID  = TP.PURPOSE_ID
        JOIN PURPOSES_VERSIONS PV ON PV.ID = P.CURRENT_VERSION_ID
        WHERE TP.TEMPLATE_ID = NEW.ID
          AND PV.REQUIRED = TRUE;

        IF V_CONFLICT_COUNT > 0 THEN 
            RAISE EXCEPTION 
                'No se puede cambiar la base a consentimiento: el template tiene % finalidad(es) '
                'REQUIRED asociadas. Reemplázalas primero.', V_CONFLICT_COUNT;
        END IF;

    -- Si la nueva base NO es consentimiento: no puede haber finalidades REVOCABLE
    ELSIF V_CONSENT_REQUIRED = FALSE THEN

        SELECT COUNT(*) INTO V_CONFLICT_COUNT
        FROM TEMPLATE_PURPOSES TP
        JOIN PURPOSES P   ON P.ID  = TP.PURPOSE_ID
        JOIN PURPOSES_VERSIONS PV ON PV.ID = P.CURRENT_VERSION_ID
        WHERE TP.TEMPLATE_ID = NEW.ID
          AND PV.REVOCABLE = TRUE;

        IF V_CONFLICT_COUNT > 0 THEN
            RAISE EXCEPTION
                'No se puede cambiar a base no-consentimiento: el template tiene % finalidad(es) '
                'REVOCABLE asociadas. Desasócialas primero.', V_CONFLICT_COUNT;
        END IF;

    END IF;

    RETURN NEW;
END;
$$ LANGUAGE PLPGSQL;
	

-- ==========================================================================================
-- TRIGGER: VALIDATE_TEMPLATE_BASIS_CHANGE
-- Ejecuta la función anterior
-- ==========================================================================================
DROP TRIGGER IF EXISTS VALIDATE_TEMPLATE_BASIS_CHANGE ON TEMPLATES;

CREATE TRIGGER VALIDATE_TEMPLATE_BASIS_CHANGE
	BEFORE UPDATE OF LEGAL_BASIS_ID
	ON TEMPLATES
	FOR EACH ROW
	EXECUTE FUNCTION TRG_VALIDATE_TEMPLATE_BASIS_CHANGE();


-- ==========================================================================================
-- EXTENSIÓN CRYPTO
-- 
-- ==========================================================================================
CREATE EXTENSION IF NOT EXISTS PGCRYPTO;

-- ==========================================================================================
-- FUNCION: GENERATE_PURPOSE_HASH
-- Genera un hash SHA-256 a partir de los campos que definen un propósito de un consentimiento
-- ==========================================================================================
CREATE OR REPLACE FUNCTION GENERATE_PURPOSE_HASH(
	P_CODE TEXT,
	P_NAME TEXT,
	P_DESCRIPTION TEXT,
	P_SHORT_DESCRIPTION TEXT,
	P_REQUIRED BOOLEAN,
	P_REVOCABLE BOOLEAN
)
RETURNS VARCHAR AS $$
BEGIN
	RETURN ENCODE(
		DIGEST(
			COALESCE(P_CODE, '') ||
			COALESCE(P_NAME, '') ||
			COALESCE(P_DESCRIPTION, '') ||
			COALESCE(P_SHORT_DESCRIPTION, '') ||
			COALESCE(P_REQUIRED::TEXT, '') ||
			COALESCE(P_REVOCABLE::TEXT, '') ||
			'SHA256'
		),
		'HEX'
	);
END;
$$ LANGUAGE PLPGSQL IMMUTABLE;

-- ==========================================================================================
-- FUNCIÓN DE TRIGGER: TRG_SET_PURPOSE_HASH
-- Calcula y asigna el hash SHA-256 ANTES DE INSERT O UPDATE EN LA TABLA DE PROPÓSITOS
-- ==========================================================================================
CREATE OR REPLACE FUNCTION TRG_SET_PURPOSE_HASH()
RETURNS TRIGGER AS $$
BEGIN 
	NEW.HASH_SHA256:= GENERATE_PURPOSE_HASH(
		NEW.CODE,
		NEW.NAME,
		NEW.DESCRIPTION,
		NEW.SHORT_DESCRIPTION,
		NEW.REQUIRED,
		NEW.REVOCABLE
	);
	RETURN NEW;
END;
$$ LANGUAGE PLPGSQL;


-- ==========================================================================================
-- TRIGGER: SET_PURPOSE_HASH
-- Se dispara antes de cada INSERT o UPDATE sobre la tabla 'purposes'
-- Ajusta el nombre de la tabla si es diferente en el esquema
-- ==========================================================================================
DROP TRIGGER IF EXISTS SET_PURPOSE_VERSION_HASH ON PURPOSES;

CREATE TRIGGER SET_PURPOSE_VERSION_HASH
	BEFORE INSERT OR UPDATE
	ON PURPOSES_VERSIONS
	FOR EACH ROW
	EXECUTE FUNCTION TRG_SET_PURPOSE_HASH();


-- ==========================================================================================
-- FUNCIÓN: GENERATE_AGREEMENT_HASH
-- 
-- ==========================================================================================
CREATE OR REPLACE FUNCTION GENERATE_AGREEMENT_HASH(P_AGREEMENT_ID UUID)
RETURNS VARCHAR AS $$
DECLARE
	V_RESULT TEXT;
BEGIN
	SELECT ENCODE(
		DIGEST(
			JSON_BUILD_OBJECT(
				'AGREEMENT', JSON_BUILD_OBJECT(
					'ID', A.ID,
					'DATA_SUBJECT_ID', A.DATA_SUBJECT_ID,
					'TEMPLATE_ID', A.TEMPLATE_ID,
					'TEMPLATE_VERSION', A.TEMPLATE_VERSION,
					'DOCUMENT_ID', A.DOCUMENT_ID,
					'CREATED_AT', A.CREATED_AT
				),
				'PURPOSES', (
					SELECT JSON_AGG(
						JSON_BUILD_OBJECT(
							'PURPOSE', AP.PURPOSE_ID,
							'ACCEPTED', AP.ACCEPTED,
							'HASH', AP.PURPOSE_HASH
						)
						ORDER BY AP.PURPOSE_ID
					)
					FROM AGREEMENTS_PURPOSES AP
					WHERE AP.AGREEMENT_ID = A.ID
				)
			)::TEXT,
			'SHA256'
		),
		'HEX'
	)
	INTO V_RESULT
	FROM AGREEMENTS A
	WHERE A.ID = P_AGREEMENT_ID;

	RETURN V_RESULT;
END;
$$ LANGUAGE PLPGSQL;
			

