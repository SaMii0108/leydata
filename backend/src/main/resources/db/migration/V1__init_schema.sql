-- V1__init_schema.sql
-- Baseline: esquema completo de LeyData, capturado con pg_dump --schema-only
-- desde la base de datos de desarrollo (leydata-consent-db / leydata_db).
-- A partir de aca, Flyway es el dueno del esquema (reemplaza las
-- migraciones sueltas V2-V7 anteriores, ya reflejadas en este baseline).

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: prevent_entity_integrity_log_mutation(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.prevent_entity_integrity_log_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    RAISE EXCEPTION 'entity_integrity_log es de solo inserción: % no permitido', TG_OP;
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: agreement_integrity_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.agreement_integrity_log (
    id uuid NOT NULL,
    agreement_id uuid NOT NULL,
    check_type character varying(255) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by uuid,
    error_detail text,
    hash_sha256 character varying(255),
    is_valid boolean NOT NULL,
    previous_hash_sha256_id character varying(255),
    recalculated_hash character varying(255) NOT NULL,
    stored_hash character varying(255) NOT NULL
);


--
-- Name: agreement_metadata; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.agreement_metadata (
    id uuid NOT NULL,
    agreement_id uuid NOT NULL,
    auth_provider character varying(255),
    capture_channel character varying(255),
    created_at timestamp(6) without time zone NOT NULL,
    extra_variables jsonb,
    ip_origin inet,
    signature_token character varying(255),
    user_agent text
);


--
-- Name: agreements; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.agreements (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    data_subject_id uuid NOT NULL,
    document_id uuid NOT NULL,
    expiration timestamp(6) without time zone,
    hash_sha256 character varying(255),
    previous_agreements_id uuid,
    previous_hash_sha256 character varying(255),
    status character varying(255) NOT NULL,
    template_id uuid NOT NULL,
    template_version integer NOT NULL
);


--
-- Name: agreements_purposes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.agreements_purposes (
    id uuid NOT NULL,
    accepted boolean NOT NULL,
    agreement_id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    expires_at timestamp(6) without time zone,
    hash_sha256 character varying(255),
    legal_basis_code character varying(255) NOT NULL,
    previous_hash_sha256 character varying(255),
    purpose_code character varying(255) NOT NULL,
    purpose_description text NOT NULL,
    purpose_hash character varying(255),
    purpose_id uuid NOT NULL,
    purpose_name character varying(255) NOT NULL,
    purpose_required boolean NOT NULL,
    purpose_revocable boolean NOT NULL,
    purpose_short_description character varying(255) NOT NULL,
    status character varying(255) NOT NULL
);


--
-- Name: data_categories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.data_categories (
    id uuid NOT NULL,
    code character varying(255) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    description text,
    is_active boolean NOT NULL,
    is_sensitive boolean NOT NULL,
    is_system boolean NOT NULL,
    name character varying(255) NOT NULL
);


--
-- Name: data_retention_policies; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.data_retention_policies (
    id uuid NOT NULL,
    anonymize_after boolean NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    is_active boolean NOT NULL,
    legal_justification text,
    purpose_data_category_id uuid NOT NULL,
    retention_period integer NOT NULL,
    retention_unit character varying(255) NOT NULL
);


--
-- Name: data_subjects; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.data_subjects (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    identifier character varying(255) NOT NULL
);


--
-- Name: document_purposes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.document_purposes (
    is_active boolean NOT NULL,
    document_id uuid NOT NULL,
    purpose_id uuid NOT NULL
);


--
-- Name: domains; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.domains (
    id uuid NOT NULL,
    active boolean NOT NULL,
    code character varying(255) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    description text,
    name character varying(255) NOT NULL
);


--
-- Name: entity_integrity_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.entity_integrity_log (
    id uuid NOT NULL,
    check_type character varying(255) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    created_by character varying(255),
    entity_id uuid NOT NULL,
    entity_type character varying(255) NOT NULL,
    error_detail text,
    hash_sha256 character varying(255),
    is_valid boolean NOT NULL,
    previous_hash_sha256_id character varying(255),
    recalculated_hash character varying(255) NOT NULL,
    stored_hash character varying(255) NOT NULL
);


--
-- Name: legal_basis_catalog; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.legal_basis_catalog (
    id uuid NOT NULL,
    code character varying(255) NOT NULL,
    consent_required boolean NOT NULL,
    description text,
    is_active boolean NOT NULL,
    name character varying(255) NOT NULL
);


--
-- Name: notifications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notifications (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    message text,
    is_read boolean NOT NULL,
    recipient_id character varying(255) NOT NULL,
    reference_id uuid,
    title character varying(255) NOT NULL,
    type character varying(255) NOT NULL,
    CONSTRAINT notifications_type_check CHECK (((type)::text = ANY ((ARRAY['PURPOSE_APPROVED'::character varying, 'PURPOSE_REJECTED'::character varying, 'DOCUMENT_PUBLISHED'::character varying, 'PURPOSE_REQUEST_FULFILLED'::character varying])::text[])))
);


--
-- Name: privacy_documents; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.privacy_documents (
    id uuid NOT NULL,
    approved_by character varying(255),
    approved_by_name character varying(255),
    category character varying(30) NOT NULL,
    content text,
    created_at timestamp(6) without time zone NOT NULL,
    created_by character varying(255) NOT NULL,
    created_by_name character varying(255),
    document_family_id uuid,
    hash_sha256 character varying(64),
    is_active boolean NOT NULL,
    name character varying(255) NOT NULL,
    pdf_content bytea,
    publish_at timestamp(6) without time zone,
    rejection_reason text,
    status character varying(20) NOT NULL,
    template_id uuid,
    updated_at timestamp(6) without time zone,
    version integer NOT NULL,
    CONSTRAINT privacy_documents_category_check CHECK (((category)::text = ANY ((ARRAY['POLITICA_PRIVACIDAD'::character varying, 'AVISO_COOKIES'::character varying, 'DATOS_SENSIBLES'::character varying, 'MARKETING_DIRECTO'::character varying, 'MENORES_EDAD'::character varying, 'TRANSFERENCIA_TERCEROS'::character varying])::text[]))),
    CONSTRAINT privacy_documents_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'IN_REVIEW'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'PUBLISHED'::character varying, 'ARCHIVED'::character varying])::text[])))
);


--
-- Name: purpose_data_categories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.purpose_data_categories (
    id uuid NOT NULL,
    data_category_id uuid NOT NULL,
    purpose_id uuid NOT NULL,
    required boolean NOT NULL
);


--
-- Name: purpose_data_category_data_uses; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.purpose_data_category_data_uses (
    purpose_data_category_id uuid NOT NULL,
    data_use character varying(255),
    CONSTRAINT purpose_data_category_data_uses_data_use_check CHECK (((data_use)::text = ANY ((ARRAY['STORAGE'::character varying, 'PROCESSING'::character varying, 'TRANSFER_TO_THIRD_PARTIES'::character varying, 'PROFILING'::character varying, 'ANALYSIS'::character varying])::text[])))
);


--
-- Name: purpose_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.purpose_requests (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    domain_id uuid NOT NULL,
    justification text NOT NULL,
    requested_data text,
    requester_id character varying(255) NOT NULL,
    requester_name character varying(255),
    review_notes text,
    reviewer_id character varying(255),
    reviewer_name character varying(255),
    status character varying(255) NOT NULL,
    title character varying(255) NOT NULL,
    updated_at timestamp(6) without time zone
);


--
-- Name: purposes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.purposes (
    id uuid NOT NULL,
    approved_by character varying(255),
    approved_by_name character varying(255),
    code character varying(255) NOT NULL,
    consent_statement text,
    created_at timestamp(6) without time zone NOT NULL,
    created_by character varying(255),
    created_by_name character varying(255),
    description text,
    domain_id uuid,
    hash_sha256 character varying(255),
    is_active boolean NOT NULL,
    legal_basis_id uuid,
    name character varying(255) NOT NULL,
    presentation_order integer,
    purpose_request_id uuid,
    required boolean NOT NULL,
    revocable boolean NOT NULL,
    short_description character varying(255),
    updated_at timestamp(6) without time zone,
    purpose_family_id uuid,
    status character varying(255),
    version integer
);


--
-- Name: system_audit_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_audit_log (
    id uuid NOT NULL,
    action character varying(255) NOT NULL,
    actor_id character varying(255),
    actor_role character varying(255) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    ip_address character varying(255),
    log_hash character varying(255) NOT NULL,
    new_data text,
    old_data text,
    previous_log_hash character varying(255),
    record_id uuid,
    request_id character varying(64),
    table_name character varying(255) NOT NULL,
    user_agent text
);


--
-- Name: template_purposes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.template_purposes (
    is_visible boolean NOT NULL,
    order_position integer,
    purpose_id uuid NOT NULL,
    template_id uuid NOT NULL
);


--
-- Name: templates; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.templates (
    id uuid NOT NULL,
    activation_date timestamp(6) with time zone,
    approved_at timestamp(6) with time zone,
    approved_by character varying(255),
    change_reason text,
    created_at timestamp(6) with time zone NOT NULL,
    created_by character varying(255),
    description text,
    hash_sha256 character varying(255),
    is_active boolean NOT NULL,
    name character varying(255) NOT NULL,
    previous_hash_sha256 character varying(255),
    template_key character varying(255) NOT NULL,
    title character varying(255),
    version integer NOT NULL,
    domain_id uuid,
    force_reconsent boolean DEFAULT false NOT NULL
);


--
-- Name: user_domains; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_domains (
    id uuid NOT NULL,
    keycloak_id character varying(255) NOT NULL,
    domain_id uuid NOT NULL
);


--
-- Name: user_status; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_status (
    keycloak_id character varying(255) NOT NULL,
    blocked boolean NOT NULL,
    blocked_at timestamp(6) without time zone
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id uuid NOT NULL,
    active boolean NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    email character varying(255) NOT NULL,
    keycloak_id character varying(255),
    name character varying(255) NOT NULL
);


--
-- Name: agreement_integrity_log agreement_integrity_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agreement_integrity_log
    ADD CONSTRAINT agreement_integrity_log_pkey PRIMARY KEY (id);


--
-- Name: agreement_metadata agreement_metadata_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agreement_metadata
    ADD CONSTRAINT agreement_metadata_pkey PRIMARY KEY (id);


--
-- Name: agreements agreements_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agreements
    ADD CONSTRAINT agreements_pkey PRIMARY KEY (id);


--
-- Name: agreements_purposes agreements_purposes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agreements_purposes
    ADD CONSTRAINT agreements_purposes_pkey PRIMARY KEY (id);


--
-- Name: data_categories data_categories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.data_categories
    ADD CONSTRAINT data_categories_pkey PRIMARY KEY (id);


--
-- Name: data_retention_policies data_retention_policies_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.data_retention_policies
    ADD CONSTRAINT data_retention_policies_pkey PRIMARY KEY (id);


--
-- Name: data_subjects data_subjects_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.data_subjects
    ADD CONSTRAINT data_subjects_pkey PRIMARY KEY (id);


--
-- Name: document_purposes document_purposes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document_purposes
    ADD CONSTRAINT document_purposes_pkey PRIMARY KEY (document_id, purpose_id);


--
-- Name: domains domains_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.domains
    ADD CONSTRAINT domains_pkey PRIMARY KEY (id);


--
-- Name: entity_integrity_log entity_integrity_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.entity_integrity_log
    ADD CONSTRAINT entity_integrity_log_pkey PRIMARY KEY (id);


--
-- Name: legal_basis_catalog legal_basis_catalog_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.legal_basis_catalog
    ADD CONSTRAINT legal_basis_catalog_pkey PRIMARY KEY (id);


--
-- Name: notifications notifications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);


--
-- Name: privacy_documents privacy_documents_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.privacy_documents
    ADD CONSTRAINT privacy_documents_pkey PRIMARY KEY (id);


--
-- Name: purpose_data_categories purpose_data_categories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purpose_data_categories
    ADD CONSTRAINT purpose_data_categories_pkey PRIMARY KEY (id);


--
-- Name: purpose_requests purpose_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purpose_requests
    ADD CONSTRAINT purpose_requests_pkey PRIMARY KEY (id);


--
-- Name: purposes purposes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purposes
    ADD CONSTRAINT purposes_pkey PRIMARY KEY (id);


--
-- Name: system_audit_log system_audit_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_audit_log
    ADD CONSTRAINT system_audit_log_pkey PRIMARY KEY (id);


--
-- Name: template_purposes template_purposes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.template_purposes
    ADD CONSTRAINT template_purposes_pkey PRIMARY KEY (purpose_id, template_id);


--
-- Name: templates templates_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.templates
    ADD CONSTRAINT templates_pkey PRIMARY KEY (id);


--
-- Name: domains uk2kqby88keuxhkucvhdhyff00t; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.domains
    ADD CONSTRAINT uk2kqby88keuxhkucvhdhyff00t UNIQUE (code);


--
-- Name: users uk366dgrd625s5659shyen79mmw; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uk366dgrd625s5659shyen79mmw UNIQUE (keycloak_id);


--
-- Name: agreements_purposes uk3xoy2975nwkf3bc0r8ye6ecjq; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agreements_purposes
    ADD CONSTRAINT uk3xoy2975nwkf3bc0r8ye6ecjq UNIQUE (agreement_id, purpose_id);


--
-- Name: agreement_integrity_log uk461907g2svxc146h09abw45f8; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agreement_integrity_log
    ADD CONSTRAINT uk461907g2svxc146h09abw45f8 UNIQUE (hash_sha256);


--
-- Name: users uk6dotkott2kjsp8vw4d0m25fb7; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uk6dotkott2kjsp8vw4d0m25fb7 UNIQUE (email);


--
-- Name: agreements_purposes uk6ue4eoioor1xn026kchb9un4; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agreements_purposes
    ADD CONSTRAINT uk6ue4eoioor1xn026kchb9un4 UNIQUE (hash_sha256);


--
-- Name: data_retention_policies uk82ybheudo4h1114f1kork2sud; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.data_retention_policies
    ADD CONSTRAINT uk82ybheudo4h1114f1kork2sud UNIQUE (purpose_data_category_id);


--
-- Name: user_domains ukcw0lpyunn9ovg1rlrbd6licmi; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_domains
    ADD CONSTRAINT ukcw0lpyunn9ovg1rlrbd6licmi UNIQUE (keycloak_id, domain_id);


--
-- Name: entity_integrity_log ukj2ii0cl0xepbmf0qw4okgowm5; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.entity_integrity_log
    ADD CONSTRAINT ukj2ii0cl0xepbmf0qw4okgowm5 UNIQUE (hash_sha256);


--
-- Name: templates ukj9cree3eaqxl0pojh2okd7y9i; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.templates
    ADD CONSTRAINT ukj9cree3eaqxl0pojh2okd7y9i UNIQUE (hash_sha256);


--
-- Name: legal_basis_catalog ukm8g0cgmggxa0l37y26xj41ir6; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.legal_basis_catalog
    ADD CONSTRAINT ukm8g0cgmggxa0l37y26xj41ir6 UNIQUE (code);


--
-- Name: agreements ukpfr2nwwqhk25x8jru8slqyn5a; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agreements
    ADD CONSTRAINT ukpfr2nwwqhk25x8jru8slqyn5a UNIQUE (hash_sha256);


--
-- Name: templates ukrcqm1h3r55uwxj78y3l0e7ejv; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.templates
    ADD CONSTRAINT ukrcqm1h3r55uwxj78y3l0e7ejv UNIQUE (domain_id, template_key, version);


--
-- Name: data_categories uktjuv6bh6ol39kq6emxdns2kas; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.data_categories
    ADD CONSTRAINT uktjuv6bh6ol39kq6emxdns2kas UNIQUE (code);


--
-- Name: user_domains user_domains_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_domains
    ADD CONSTRAINT user_domains_pkey PRIMARY KEY (id);


--
-- Name: user_status user_status_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_status
    ADD CONSTRAINT user_status_pkey PRIMARY KEY (keycloak_id);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: entity_integrity_log trg_entity_integrity_log_no_update; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_entity_integrity_log_no_update BEFORE DELETE OR UPDATE ON public.entity_integrity_log FOR EACH ROW EXECUTE FUNCTION public.prevent_entity_integrity_log_mutation();


--
-- Name: agreements fk3un2c7uy7i1mr5hxha1wy5dgv; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agreements
    ADD CONSTRAINT fk3un2c7uy7i1mr5hxha1wy5dgv FOREIGN KEY (previous_agreements_id) REFERENCES public.agreements(id);


--
-- Name: agreements_purposes fk440y2qn063j1roowmvmgk352j; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agreements_purposes
    ADD CONSTRAINT fk440y2qn063j1roowmvmgk352j FOREIGN KEY (agreement_id) REFERENCES public.agreements(id);


--
-- Name: agreements fk4i4l6pm13d4newo9j16708dq6; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agreements
    ADD CONSTRAINT fk4i4l6pm13d4newo9j16708dq6 FOREIGN KEY (document_id) REFERENCES public.privacy_documents(id);


--
-- Name: document_purposes fk4ifptavnf7s102myxwvllvv54; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document_purposes
    ADD CONSTRAINT fk4ifptavnf7s102myxwvllvv54 FOREIGN KEY (document_id) REFERENCES public.privacy_documents(id);


--
-- Name: document_purposes fk4kon2sl9dr9fe5du1ad6uspjm; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document_purposes
    ADD CONSTRAINT fk4kon2sl9dr9fe5du1ad6uspjm FOREIGN KEY (purpose_id) REFERENCES public.purposes(id);


--
-- Name: template_purposes fk6qsbrr48e4id647pg53l7am5b; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.template_purposes
    ADD CONSTRAINT fk6qsbrr48e4id647pg53l7am5b FOREIGN KEY (template_id) REFERENCES public.templates(id);


--
-- Name: purpose_data_categories fk7crf8atay25057c53wxxu5k5; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purpose_data_categories
    ADD CONSTRAINT fk7crf8atay25057c53wxxu5k5 FOREIGN KEY (purpose_id) REFERENCES public.purposes(id);


--
-- Name: templates fk98yq9oibjcn06lt9hv7ubbyaf; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.templates
    ADD CONSTRAINT fk98yq9oibjcn06lt9hv7ubbyaf FOREIGN KEY (domain_id) REFERENCES public.domains(id);


--
-- Name: purpose_data_category_data_uses fk9sy2n12i0ak83hnttxl2cy9q2; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purpose_data_category_data_uses
    ADD CONSTRAINT fk9sy2n12i0ak83hnttxl2cy9q2 FOREIGN KEY (purpose_data_category_id) REFERENCES public.purpose_data_categories(id);


--
-- Name: user_domains fkc1ksynw7ga78xtxjcrff3t4dn; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_domains
    ADD CONSTRAINT fkc1ksynw7ga78xtxjcrff3t4dn FOREIGN KEY (domain_id) REFERENCES public.domains(id);


--
-- Name: agreements fkejx7h7l4xr6wv8me9uikn37s1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agreements
    ADD CONSTRAINT fkejx7h7l4xr6wv8me9uikn37s1 FOREIGN KEY (data_subject_id) REFERENCES public.data_subjects(id);


--
-- Name: purposes fken545d2mxuyts5aoipibxgtej; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purposes
    ADD CONSTRAINT fken545d2mxuyts5aoipibxgtej FOREIGN KEY (legal_basis_id) REFERENCES public.legal_basis_catalog(id);


--
-- Name: purpose_data_categories fkfs83sg1d06c05la88vs9q1hsr; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purpose_data_categories
    ADD CONSTRAINT fkfs83sg1d06c05la88vs9q1hsr FOREIGN KEY (data_category_id) REFERENCES public.data_categories(id);


--
-- Name: template_purposes fkgcd6gv7gyvkgwqsl04oyu0h43; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.template_purposes
    ADD CONSTRAINT fkgcd6gv7gyvkgwqsl04oyu0h43 FOREIGN KEY (purpose_id) REFERENCES public.purposes(id);


--
-- Name: purposes fkhegjv30wbacykav32tbf31xem; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purposes
    ADD CONSTRAINT fkhegjv30wbacykav32tbf31xem FOREIGN KEY (domain_id) REFERENCES public.domains(id);


--
-- Name: agreement_integrity_log fkhfnw9r9l579xp2hjyk16xacm0; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agreement_integrity_log
    ADD CONSTRAINT fkhfnw9r9l579xp2hjyk16xacm0 FOREIGN KEY (agreement_id) REFERENCES public.agreements(id);


--
-- Name: agreement_metadata fklftajosi7vsspvln4tx1kv72o; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agreement_metadata
    ADD CONSTRAINT fklftajosi7vsspvln4tx1kv72o FOREIGN KEY (agreement_id) REFERENCES public.agreements(id);


--
-- Name: privacy_documents fko32p5lke2lk55jklt71nmutu6; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.privacy_documents
    ADD CONSTRAINT fko32p5lke2lk55jklt71nmutu6 FOREIGN KEY (template_id) REFERENCES public.templates(id);


--
-- Name: purpose_requests fkoa2wapcy67io5cugyvscxaa7g; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purpose_requests
    ADD CONSTRAINT fkoa2wapcy67io5cugyvscxaa7g FOREIGN KEY (domain_id) REFERENCES public.domains(id);


--
-- Name: agreements fkr25h9j2s7niwebwh97c4y9a78; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agreements
    ADD CONSTRAINT fkr25h9j2s7niwebwh97c4y9a78 FOREIGN KEY (template_id) REFERENCES public.templates(id);


--
-- Name: data_retention_policies fks0wm54xl0crb9d2nstjy4yf73; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.data_retention_policies
    ADD CONSTRAINT fks0wm54xl0crb9d2nstjy4yf73 FOREIGN KEY (purpose_data_category_id) REFERENCES public.purpose_data_categories(id);


--
-- Name: purposes fksi7hn5ncndlesdu98g2crwdg8; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purposes
    ADD CONSTRAINT fksi7hn5ncndlesdu98g2crwdg8 FOREIGN KEY (purpose_request_id) REFERENCES public.purpose_requests(id);
