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