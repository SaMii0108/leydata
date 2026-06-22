-- ==========================================================================================
-- TABLA: USER_ROLE
-- Relación muchos a muchos entre usuarios y roles
-- Un usuario puede tener múltiples roles; un rol puede esta asignado a múltiples usuarios
-- La combinación (USER_ID, ROLE_ID es única, no se permiten asignaciones duplicadas 
-- ==========================================================================================
CREATE TABLE USERS_ROLE(
	USER_ID UUID NOT NULL,
	ROLE_ID INTEGER NOT NULL,
	PRIMARY KEY (USER_ID, ROLE_ID)
);

ALTER TABLE USERS_ROLE ADD CONSTRAINT FK_USERS_ROLE_USER
FOREIGN KEY (USER_ID) REFERENCES USERS(ID) ON DELETE CASCADE;

ALTER TABLE USERS_ROLE ADD CONSTRAINT FK_USERS_ROLE_ROLE
FOREIGN KEY (ROLE_ID) REFERENCES ROLE(ID) ON DELETE RESTRICT; 