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