-- El versionado de purposes (purpose_family_id + version) requiere que varias filas
-- compartan el mismo `code` entre versiones de una misma familia — igual que
-- TEMPLATES.template_key no es único entre versiones de un mismo template.
-- El UNIQUE constraint original sobre purposes.code bloqueaba POST /api/purposes/{id}/new-version
-- con un 500 (violación de constraint). La unicidad de `code` para finalidades NUEVAS
-- (distinta familia) se sigue validando a nivel de aplicación en PurposeService.create()
-- vía PurposesRepository.existsByCode().
-- Idempotente: no falla si el constraint ya fue eliminado.

DO $$
DECLARE
    constraint_name text;
BEGIN
    SELECT con.conname INTO constraint_name
    FROM pg_constraint con
    JOIN pg_class rel ON rel.oid = con.conrelid
    WHERE rel.relname = 'purposes'
      AND con.contype = 'u'
      AND con.conkey = (
          SELECT array_agg(attnum)
          FROM pg_attribute
          WHERE attrelid = rel.oid AND attname = 'code'
      );

    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE purposes DROP CONSTRAINT %I', constraint_name);
    END IF;
END $$;
