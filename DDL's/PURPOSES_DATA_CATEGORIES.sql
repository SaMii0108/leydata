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