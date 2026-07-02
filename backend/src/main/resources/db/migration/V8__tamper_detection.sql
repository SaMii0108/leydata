-- V8: Detección de mutaciones directas en agreements y agreements_purposes.
-- Cualquier UPDATE o DELETE que bypasee la API queda registrado en db_tamper_log.
-- El scheduler TamperDetectionScheduler.java lo lee y lo eleva al audit chain oficial.

CREATE TABLE IF NOT EXISTS public.db_tamper_log (
    id            uuid                        NOT NULL DEFAULT gen_random_uuid(),
    table_name    character varying(100)      NOT NULL,
    operation     character varying(10)       NOT NULL,  -- UPDATE | DELETE
    record_id     uuid                        NOT NULL,
    old_data      text,
    new_data      text,
    db_user       character varying(255)      NOT NULL,
    detected_at   timestamp without time zone NOT NULL DEFAULT now(),
    processed     boolean                     NOT NULL DEFAULT false,
    CONSTRAINT db_tamper_log_pkey PRIMARY KEY (id)
);

-- Índice para que el scheduler recupere sólo los no procesados rápido
CREATE INDEX IF NOT EXISTS idx_db_tamper_log_unprocessed
    ON public.db_tamper_log (processed, detected_at)
    WHERE processed = false;

-- ── Función genérica reutilizada por ambos triggers ───────────────────────────

CREATE OR REPLACE FUNCTION public.log_direct_mutation()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.db_tamper_log (table_name, operation, record_id, old_data, new_data, db_user)
    VALUES (
        TG_TABLE_NAME,
        TG_OP,
        OLD.id,
        row_to_json(OLD)::text,
        CASE WHEN TG_OP = 'DELETE' THEN NULL ELSE row_to_json(NEW)::text END,
        current_user
    );

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ── Trigger sobre agreements ──────────────────────────────────────────────────

DROP TRIGGER IF EXISTS trg_agreements_tamper_detect ON public.agreements;
CREATE TRIGGER trg_agreements_tamper_detect
    AFTER UPDATE OR DELETE ON public.agreements
    FOR EACH ROW
    EXECUTE FUNCTION public.log_direct_mutation();

-- ── Trigger sobre agreements_purposes ────────────────────────────────────────

DROP TRIGGER IF EXISTS trg_agreements_purposes_tamper_detect ON public.agreements_purposes;
CREATE TRIGGER trg_agreements_purposes_tamper_detect
    AFTER UPDATE OR DELETE ON public.agreements_purposes
    FOR EACH ROW
    EXECUTE FUNCTION public.log_direct_mutation();
