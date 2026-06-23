ALTER TABLE privacy_documents
  ADD COLUMN IF NOT EXISTS document_family_id UUID;

-- Inicializar registros existentes: family_id = id propio
UPDATE privacy_documents
SET document_family_id = id
WHERE document_family_id IS NULL;
