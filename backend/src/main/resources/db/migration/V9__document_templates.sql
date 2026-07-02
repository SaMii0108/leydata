-- V9: relación documento↔template pasa de 1-a-1 (privacy_documents.template_id) a muchos-a-muchos.
-- Un documento puede tener cero, uno o varios templates asociados, en cualquier estado del
-- documento y sin exigir que el template esté ACTIVE. La asociación es propiedad del documento.

CREATE TABLE IF NOT EXISTS public.document_templates (
    document_id uuid    NOT NULL,
    template_id uuid    NOT NULL,
    is_active   boolean NOT NULL DEFAULT true,
    CONSTRAINT document_templates_pkey PRIMARY KEY (document_id, template_id),
    CONSTRAINT fk_document_templates_document FOREIGN KEY (document_id) REFERENCES public.privacy_documents(id),
    CONSTRAINT fk_document_templates_template FOREIGN KEY (template_id) REFERENCES public.templates(id)
);

-- Migrar los vínculos existentes (privacy_documents.template_id) a la nueva tabla de unión.
INSERT INTO public.document_templates (document_id, template_id, is_active)
SELECT id, template_id, true
FROM public.privacy_documents
WHERE template_id IS NOT NULL;

-- Eliminar la columna y su FK: el vínculo vive ahora únicamente en document_templates.
ALTER TABLE public.privacy_documents DROP CONSTRAINT IF EXISTS fko32p5lke2lk55jklt71nmutu6;
ALTER TABLE public.privacy_documents DROP COLUMN IF EXISTS template_id;
