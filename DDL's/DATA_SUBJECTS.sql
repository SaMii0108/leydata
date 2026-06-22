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
