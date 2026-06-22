-- ==========================================================================================
-- TABLA: ROLE
-- Catálogo de roles disponibles en el sistema (DPO, LEGAL, ADMIN, JEFE_DOMINIO)
-- Define los perfiles de acceso y responsabilidad dentro del módulo
-- Los roles son asignados por un ADMIN y determinan los permisos operativos del usuario
-- ==========================================================================================
CREATE TABLE ROLE(
	ID SERIAL PRIMARY KEY,
	CODE VARCHAR(50) NOT NULL UNIQUE, --DPO, LEGAL, ADMIN, JEFE_DOMINIO
	NAME VARCHAR(100) NOT NULL
);