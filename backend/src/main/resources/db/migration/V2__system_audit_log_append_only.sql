CREATE FUNCTION prevent_system_audit_log_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    RAISE EXCEPTION 'system_audit_log es de solo inserción: % no permitido', TG_OP;
END;
$$;

CREATE TRIGGER trg_system_audit_log_no_update
    BEFORE DELETE OR UPDATE ON system_audit_log
    FOR EACH ROW EXECUTE FUNCTION prevent_system_audit_log_mutation();
