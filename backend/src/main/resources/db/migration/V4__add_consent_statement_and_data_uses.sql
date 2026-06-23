-- 1. Texto de aceptación legal visible al titular (nullable: puede existir purpose sin statement aún)
ALTER TABLE purposes
  ADD COLUMN IF NOT EXISTS consent_statement TEXT;

-- 2. Usos declarados de cada categoría de dato por finalidad
--    Una categoría puede tener múltiples usos (almacenamiento, procesamiento, etc.)
CREATE TABLE IF NOT EXISTS purpose_data_category_data_uses (
    purpose_data_category_id UUID NOT NULL
        REFERENCES purpose_data_categories(id) ON DELETE CASCADE,
    data_use VARCHAR(50) NOT NULL,
    PRIMARY KEY (purpose_data_category_id, data_use)
);
