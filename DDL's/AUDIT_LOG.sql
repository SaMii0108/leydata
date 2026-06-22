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