ALTER TABLE data_categories
  ADD COLUMN IF NOT EXISTS is_system   BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS created_at  TIMESTAMP NOT NULL DEFAULT NOW();

-- Las categorías ya existentes se marcan como no-system (custom)
-- El seeder de arranque creará las categorías del sistema correctamente
