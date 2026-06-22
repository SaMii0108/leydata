-- ==========================================================================================
-- TABLA: USERS
-- Registro de usuarios del sistema con acceso al módulo
-- Almacena identidad, estado de activación y fecha de incorporación
-- Todo usuario debe tener al menos un rol asignado para operar en el sistema
-- ==========================================================================================
CREATE TABLE USERS(
	ID UUID PRIMARY KEY DEFAULT GEN_RANDOM_UUID(),
	EMAIL VARCHAR(255) NOT NULL UNIQUE,
	NAME VARCHAR(200) NOT NULL,
	PASSWORD VARCHAR(256) NOT NULL,
	IS_ACTIVE BOOLEAN NOT NULL DEFAULT TRUE,
	IS_BLOCKED BOOLEAN NOT NULL DEFAULT FALSE,
	CREATED_AT TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- AGREGAR REGLA SI EL USARIO ESTA BLOQUEADO (DESPEDIDO) NO PUEDE ESTAR ACTIVO (EL IS ACTIVE ES TEMPORAL)