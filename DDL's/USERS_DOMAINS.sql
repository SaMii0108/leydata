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
