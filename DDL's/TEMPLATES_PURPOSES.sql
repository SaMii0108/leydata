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