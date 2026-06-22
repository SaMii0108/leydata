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