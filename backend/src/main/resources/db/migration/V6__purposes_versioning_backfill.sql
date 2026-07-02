-- Backfill de versionado para purposes existentes.
-- Aplicar manualmente DESPUÉS de que el backend haya levantado al menos una vez
-- (Hibernate crea las columnas purpose_family_id/version/status vía ddl-auto=update;
-- este script solo rellena los valores para las filas creadas antes de esa migración).
-- Idempotente: WHERE ... IS NULL evita sobreescribir filas ya migradas.

UPDATE purposes
SET purpose_family_id = id
WHERE purpose_family_id IS NULL;

UPDATE purposes
SET version = 1
WHERE version IS NULL;

UPDATE purposes
SET status = 'ACTIVE'
WHERE status IS NULL;
