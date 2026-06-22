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