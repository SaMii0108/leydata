-- Trigger de solo-inserción para entity_integrity_log.
-- Aplicar manualmente DESPUÉS de que el backend haya levantado al menos una vez
-- (Hibernate crea la tabla vía ddl-auto=update; este script no puede correr antes).
-- Idempotente: re-ejecutable sin error.

CREATE OR REPLACE FUNCTION prevent_entity_integrity_log_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'entity_integrity_log es de solo inserción: % no permitido', TG_OP;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_entity_integrity_log_no_update ON entity_integrity_log;
CREATE TRIGGER trg_entity_integrity_log_no_update
    BEFORE UPDATE OR DELETE ON entity_integrity_log
    FOR EACH ROW
    EXECUTE FUNCTION prevent_entity_integrity_log_mutation();
