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