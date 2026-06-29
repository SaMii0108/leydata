package com.leydata.backend.seeder;

import com.leydata.backend.entity.*;
import com.leydata.backend.orgdomain.infrastructure.persistence.DomainsRepository;
import com.leydata.backend.privacydoc.domain.enums.DocumentCategory;
import com.leydata.backend.privacydoc.domain.enums.DocumentStatus;
import com.leydata.backend.privacydoc.infrastructure.persistence.DocumentPurposesRepository;
import com.leydata.backend.privacydoc.infrastructure.persistence.PrivacyDocumentsRepository;
import com.leydata.backend.purpose.infrastructure.persistence.PurposeRequestsRepository;
import com.leydata.backend.purposes.infrastructure.persistence.PurposesRepository;
import com.leydata.backend.agreement.infrastructure.persistence.AgreementMetadataRepository;
import com.leydata.backend.agreement.infrastructure.persistence.AgreementsPurposesRepository;
import com.leydata.backend.agreement.infrastructure.persistence.AgreementsRepository;
import com.leydata.backend.repository.DataCategoriesRepository;
import com.leydata.backend.repository.DataRetentionPoliciesRepository;
import com.leydata.backend.repository.DataSubjectsRepository;
import com.leydata.backend.repository.LegalBasisCatalogRepository;
import com.leydata.backend.repository.PurposeDataCategoriesRepository;
import com.leydata.backend.template.infrastructure.persistence.TemplatePurposesRepository;
import com.leydata.backend.template.infrastructure.persistence.TemplatesRepository;
import com.leydata.backend.user.infrastructure.persistence.UsersRepository;
import com.leydata.backend.userdomain.domain.UserDomain;
import com.leydata.backend.userdomain.infrastructure.persistence.UserDomainRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Pobla la BD con datos realistas de prueba para "MedVida SpA" (empresa de
 * salud complementaria).
 * Solo se activa con el perfil Spring: test-data
 * ./mvnw spring-boot:run -Dspring-boot.run.profiles=test-data
 * Idempotente: si ya existen dominios, no hace nada.
 */
@Slf4j
@Component
@Profile("test-data")
@Order(2)
@RequiredArgsConstructor
public class TestDataSeeder implements CommandLineRunner {

    private static final String KC_DPO      = "aaaaaaaa-0001-0001-0001-000000000001";
    private static final String KC_JEFE_MKT = "aaaaaaaa-0002-0002-0002-000000000002";
    private static final String KC_JEFE_TI  = "aaaaaaaa-0003-0003-0003-000000000003";
    private static final String KC_USER     = "aaaaaaaa-0004-0004-0004-000000000004";

    private final UsersRepository usersRepo;
    private final DomainsRepository domainsRepo;
    private final UserDomainRepository userDomainRepo;
    private final PurposeRequestsRepository purposeRequestsRepo;
    private final PurposesRepository purposesRepo;
    private final LegalBasisCatalogRepository legalBasisRepo;
    private final DataCategoriesRepository dataCategoriesRepo;
    private final PurposeDataCategoriesRepository purposeDataCategoriesRepo;
    private final DataRetentionPoliciesRepository retentionPoliciesRepo;
    private final TemplatesRepository templatesRepo;
    private final TemplatePurposesRepository templatePurposesRepo;
    private final DataSubjectsRepository dataSubjectsRepo;
    private final PrivacyDocumentsRepository privacyDocsRepo;
    private final DocumentPurposesRepository docPurposesRepo;
    private final AgreementsRepository agreementsRepo;
    private final AgreementsPurposesRepository agreementsPurposesRepo;
    private final AgreementMetadataRepository agreementMetadataRepo;

    private static final LocalDateTime NOW = LocalDateTime.of(2025, 6, 1, 10, 0);

    @Override
    public void run(String... args) {
        if (domainsRepo.count() > 0) {
            log.info("[TestDataSeeder] Datos de prueba ya presentes — omitiendo.");
            return;
        }
        log.info("[TestDataSeeder] Cargando datos de prueba para MedVida SpA...");

        List<LegalBasisCatalog> bases = seedLegalBasis();
        List<DataCategories> categories = seedDataCategories();
        List<Domains> domains = seedDomains();
        List<Users> users = seedUsers();
        seedUserDomains(users, domains);
        List<PurposeRequests> requests = seedPurposeRequests(users, domains);
        List<Purposes> purposes = seedPurposes(bases, domains, users, requests);
        List<PurposeDataCategories> pdcs = seedPurposeDataCategories(purposes, categories);
        seedRetentionPolicies(pdcs);
        List<Templates> templates = seedTemplates();
        seedTemplatePurposes(templates, purposes);
        List<DataSubjects> subjects = seedDataSubjects();
        List<PrivacyDocuments> docs = seedPrivacyDocuments(templates, users);
        seedDocumentPurposes(docs, purposes);
        seedAgreements(subjects, templates, docs, purposes);

        log.info("[TestDataSeeder] ✔ Datos de prueba cargados exitosamente.");
    }

    // ── 1. BASES LEGALES ─────────────────────────────────────────────────────────

    private List<LegalBasisCatalog> seedLegalBasis() {
        LegalBasisCatalog art12c = new LegalBasisCatalog();
        art12c.setCode("ART_12_CONSENTIMIENTO");
        art12c.setName("Consentimiento del titular");
        art12c.setDescription(
                "Art. 12 Ley 21.719 — tratamiento basado en consentimiento libre, informado y específico del titular.");
        art12c.setConsentRequired(true);
        art12c.setIsActive(true);

        LegalBasisCatalog art12k = new LegalBasisCatalog();
        art12k.setCode("ART_12_CONTRATO");
        art12k.setName("Ejecución de contrato");
        art12k.setDescription(
                "Art. 12 Ley 21.719 — necesario para la ejecución de un contrato del que el titular es parte.");
        art12k.setConsentRequired(false);
        art12k.setIsActive(true);

        LegalBasisCatalog art13 = new LegalBasisCatalog();
        art13.setCode("ART_13_DATOS_SENSIBLES");
        art13.setName("Tratamiento de datos sensibles");
        art13.setDescription(
                "Art. 13 Ley 21.719 — consentimiento explícito requerido para salud, biométricos u origen racial.");
        art13.setConsentRequired(true);
        art13.setIsActive(true);

        LegalBasisCatalog art16 = new LegalBasisCatalog();
        art16.setCode("ART_16_CESION");
        art16.setName("Cesión a terceros");
        art16.setDescription(
                "Art. 16 Ley 21.719 — cesión de datos a terceros con consentimiento o interés legítimo acreditado.");
        art16.setConsentRequired(true);
        art16.setIsActive(true);

        return legalBasisRepo.saveAll(List.of(art12c, art12k, art13, art16));
    }

    // ── 2. CATEGORÍAS DE DATOS ───────────────────────────────────────────────────

    private List<DataCategories> seedDataCategories() {
        return dataCategoriesRepo.saveAll(List.of(
                category("EMAIL", "Correo electrónico", "Dirección de email del titular.", false),
                category("NOMBRE_COMPLETO", "Nombre completo", "Nombre y apellidos del titular.", false),
                category("RUT", "RUT", "Rol Único Tributario / cédula de identidad chilena.", false),
                category("TELEFONO", "Número telefónico", "Teléfono fijo o móvil del titular.", false),
                category("DATOS_SALUD", "Datos de salud", "Diagnósticos, medicamentos, historial médico.", true),
                category("DATOS_FINANCIEROS", "Datos financieros", "Ingresos, deudas, historial de pagos.", true)));
    }

    private DataCategories category(String code, String name, String desc, boolean sensitive) {
        DataCategories c = new DataCategories();
        c.setCode(code);
        c.setName(name);
        c.setDescription(desc);
        c.setIsSensitive(sensitive);
        c.setIsActive(true);
        return c;
    }

    // ── 3. DOMINIOS ──────────────────────────────────────────────────────────────

    private List<Domains> seedDomains() {
        return domainsRepo.saveAll(List.of(
                domain("MKT", "Marketing", "Dominio de marketing y comunicaciones con afiliados."),
                domain("LEGAL", "Legal y Cumplimiento", "Dominio de cumplimiento normativo y contratos."),
                domain("TI", "Tecnología e Información", "Dominio de sistemas, datos y analítica.")));
    }

    private Domains domain(String code, String name, String desc) {
        Domains d = new Domains();
        d.setCode(code);
        d.setName(name);
        d.setDescription(desc);
        d.setCreatedAt(NOW);
        d.setActive(true);
        return d;
    }

    // ── 4. USUARIOS ──────────────────────────────────────────────────────────────
    // Índice: 0=dpo, 1=jefe-mkt, 2=jefe-ti, 3=user

    private List<Users> seedUsers() {
        return usersRepo.saveAll(List.of(
                user(KC_DPO,      "dpo@medvida.cl",         "Carmen Soto Vera"),
                user(KC_JEFE_MKT, "m.gutierrez@medvida.cl", "Mauricio Gutiérrez"),
                user(KC_JEFE_TI,  "f.torres@medvida.cl",    "Felipe Torres Muñoz"),
                user(KC_USER,     "a.reyes@medvida.cl",      "Andrea Reyes Díaz")));
    }

    private Users user(String keycloakId, String email, String name) {
        Users u = new Users();
        u.setKeycloakId(keycloakId);
        u.setEmail(email);
        u.setName(name);
        u.setActive(true);
        u.setCreatedAt(NOW);
        return u;
    }

    // ── 5. ASIGNACIÓN USUARIO-DOMINIO ────────────────────────────────────────────
    // Índice de domains: 0=MKT, 1=LEGAL, 2=TI

    private void seedUserDomains(List<Users> users, List<Domains> domains) {
        userDomainRepo.save(UserDomain.builder()
                .keycloakId(users.get(1).getKeycloakId())
                .domain(domains.get(0))
                .build()); // m.gutierrez → MKT
        userDomainRepo.save(UserDomain.builder()
                .keycloakId(users.get(2).getKeycloakId())
                .domain(domains.get(2))
                .build()); // f.torres → TI
    }

    // ── 6. SOLICITUDES DE PROPÓSITO ──────────────────────────────────────────────

    private List<PurposeRequests> seedPurposeRequests(List<Users> users, List<Domains> domains) {
        Users dpo = users.get(0);
        Users jefeMkt = users.get(1);
        Users jefeTi = users.get(2);
        Domains mkt = domains.get(0);
        Domains ti = domains.get(2);

        PurposeRequests approved = new PurposeRequests();
        approved.setDomainId(mkt.getId());
        approved.setRequesterId(jefeMkt.getKeycloakId());
        approved.setTitle("Campaña de salud preventiva 2025");
        approved.setJustification(
                "Se requiere enviar campañas de vacunación y control preventivo a afiliados activos con consentimiento.");
        approved.setRequestedData("{\"datos\": [\"EMAIL\", \"NOMBRE_COMPLETO\", \"RUT\"]}");
        approved.setStatus("APPROVED");
        approved.setReviewerId(dpo.getKeycloakId());
        approved.setReviewNotes("Solicitud aprobada. Base legal ART_12_CONSENTIMIENTO verificada.");
        approved.setCreatedAt(NOW.minusDays(30));
        approved.setUpdatedAt(NOW.minusDays(25));

        PurposeRequests pending = new PurposeRequests();
        pending.setDomainId(ti.getId());
        pending.setRequesterId(jefeTi.getKeycloakId());
        pending.setTitle("Análisis de riesgo actuarial anonimizado");
        pending.setJustification(
                "Análisis estadístico interno para ajuste de prima basado en datos anonimizados de siniestros.");
        pending.setRequestedData("{\"datos\": [\"DATOS_SALUD\"]}");
        pending.setStatus("PENDING");
        pending.setCreatedAt(NOW.minusDays(5));

        PurposeRequests rejected = new PurposeRequests();
        rejected.setDomainId(mkt.getId());
        rejected.setRequesterId(jefeMkt.getKeycloakId());
        rejected.setTitle("Boletín informativo de salud mensual");
        rejected.setJustification("Envío de boletín con consejos de salud y promociones de coberturas adicionales.");
        rejected.setRequestedData("{\"datos\": [\"EMAIL\", \"DATOS_SALUD\"]}");
        rejected.setStatus("REJECTED");
        rejected.setReviewerId(dpo.getKeycloakId());
        rejected.setReviewNotes(
                "Rechazada: incluye DATOS_SALUD sin base legal ART_13 explícita. Reenviar con justificación del Art. 13.");
        rejected.setCreatedAt(NOW.minusDays(15));
        rejected.setUpdatedAt(NOW.minusDays(12));

        return purposeRequestsRepo.saveAll(List.of(approved, pending, rejected));
    }

    // ── 7. PROPÓSITOS ────────────────────────────────────────────────────────────

    private List<Purposes> seedPurposes(List<LegalBasisCatalog> bases, List<Domains> domains,
            List<Users> users, List<PurposeRequests> requests) {
        LegalBasisCatalog art12c = bases.get(0); // ART_12_CONSENTIMIENTO
        LegalBasisCatalog art12k = bases.get(1); // ART_12_CONTRATO
        LegalBasisCatalog art13 = bases.get(2);  // ART_13_DATOS_SENSIBLES

        Domains mkt = domains.get(0);
        Domains legal = domains.get(1);
        Domains ti = domains.get(2);

        Users dpo = users.get(0);
        PurposeRequests approvedReq = requests.get(0);

        Purposes campanas = purpose(
                "MKTG_CAMPANAS_PREVENTIVAS",
                "Campañas de salud preventiva",
                "Envío de comunicaciones sobre vacunación, controles periódicos y alertas de salud preventiva a afiliados con consentimiento activo.",
                "Envío de alertas y campañas de salud preventiva.",
                art12c, mkt, dpo, false, true, 1);
        campanas.setPurposeRequestId(approvedReq.getId());
        campanas.setHashSha256("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2");

        Purposes facturacion = purpose(
                "LEGAL_FACTURACION",
                "Facturación y cobro de primas",
                "Tratamiento de RUT y datos financieros necesarios para la emisión de boletas, cobro de prima mensual y gestión de mora.",
                "Facturación y cobro mensual de plan.",
                art12k, legal, dpo, true, false, 1);
        facturacion.setHashSha256("b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3");

        Purposes mejora = purpose(
                "TI_MEJORA_SERVICIO",
                "Mejora del servicio y analítica interna",
                "Análisis estadístico anonimizado para optimizar la experiencia digital y detectar patrones de uso en la plataforma.",
                "Análisis estadístico para mejorar el servicio.",
                art12k, ti, dpo, false, true, 2);
        mejora.setHashSha256("c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4");

        Purposes datosClinicos = purpose(
                "SALUD_DATOS_CLINICOS",
                "Tratamiento de datos clínicos",
                "Registro y tratamiento de diagnósticos, medicamentos e historial médico del afiliado para la gestión de coberturas y reembolsos.",
                "Registro de historial clínico para coberturas.",
                art13, legal, dpo, true, false, 1);
        datosClinicos.setHashSha256("d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5");

        return purposesRepo.saveAll(List.of(campanas, facturacion, mejora, datosClinicos));
    }

    private Purposes purpose(String code, String name, String desc, String shortDesc,
            LegalBasisCatalog basis, Domains domain, Users creator,
            boolean required, boolean revocable, int order) {
        Purposes p = new Purposes();
        p.setCode(code);
        p.setName(name);
        p.setDescription(desc);
        p.setShortDescription(shortDesc);
        p.setRequired(required);
        p.setRevocable(revocable);
        p.setPresentationOrder(order);
        p.setLegalBasisId(basis.getId());
        p.setDomainId(domain.getId());
        p.setIsActive(true);
        p.setCreatedBy(creator.getKeycloakId());
        p.setApprovedBy(creator.getKeycloakId());
        p.setCreatedAt(NOW.minusDays(20));
        p.setUpdatedAt(NOW.minusDays(20));
        return p;
    }

    // ── 8. CATEGORÍAS DE DATOS POR PROPÓSITO ────────────────────────────────────

    private List<PurposeDataCategories> seedPurposeDataCategories(List<Purposes> purposes,
            List<DataCategories> cats) {
        // cats: 0=EMAIL, 1=NOMBRE_COMPLETO, 2=RUT, 3=TELEFONO, 4=DATOS_SALUD,
        // 5=DATOS_FINANCIEROS
        Purposes campanas = purposes.get(0);
        Purposes facturacion = purposes.get(1);
        Purposes mejora = purposes.get(2);
        Purposes datosClinicos = purposes.get(3);

        return purposeDataCategoriesRepo.saveAll(List.of(
                pdc(campanas, cats.get(0), false), // MKTG_CAMPANAS → EMAIL (opcional)
                pdc(campanas, cats.get(1), true),  // MKTG_CAMPANAS → NOMBRE_COMPLETO
                pdc(campanas, cats.get(2), true),  // MKTG_CAMPANAS → RUT
                pdc(facturacion, cats.get(2), true),   // LEGAL_FACTURACION → RUT
                pdc(facturacion, cats.get(5), true),   // LEGAL_FACTURACION → DATOS_FINANCIEROS
                pdc(mejora, cats.get(0), false),        // TI_MEJORA → EMAIL (opcional)
                pdc(datosClinicos, cats.get(2), true), // SALUD_CLINICOS → RUT
                pdc(datosClinicos, cats.get(4), true)  // SALUD_CLINICOS → DATOS_SALUD
        ));
    }

    private PurposeDataCategories pdc(Purposes purpose, DataCategories cat, boolean required) {
        PurposeDataCategories p = new PurposeDataCategories();
        p.setPurposeId(purpose.getId());
        p.setDataCategoryId(cat.getId());
        p.setRequired(required);
        return p;
    }

    // ── 9. POLÍTICAS DE RETENCIÓN ───────────────────────────────────────────────

    private void seedRetentionPolicies(List<PurposeDataCategories> pdcs) {
        int[] periods = { 12, 24, 60, 60, 36, 84, 60, 120 };
        String[] justifications = {
                "Email para contacto preventivo: 1 año desde último envío (Art. 12 Ley 21.719).",
                "Nombre completo requerido durante vigencia del plan más 2 años (Art. 12 Ley 21.719).",
                "RUT: conservar durante vigencia del plan más 5 años por obligación tributaria.",
                "RUT para facturación: conservar según SII por 6 años desde emisión de boleta.",
                "Datos de facturación: mínimo 3 años según normativa tributaria vigente.",
                "Datos financieros históricos: 7 años para auditoría regulatoria (CMF).",
                "RUT para historial médico: conservar durante vigencia de cobertura más 5 años.",
                "Datos de salud: 10 años según normativa MINSAL (Resolución 15/2007)."
        };

        for (int i = 0; i < pdcs.size(); i++) {
            DataRetentionPolicies policy = new DataRetentionPolicies();
            policy.setPurposeDataCategoryId(pdcs.get(i).getId());
            policy.setRetentionPeriod(periods[i]);
            policy.setRetentionUnit("MONTHS");
            policy.setLegalJustification(justifications[i]);
            policy.setAnonymizeAfter(true);
            policy.setIsActive(true);
            policy.setCreatedAt(NOW.minusDays(20));
            retentionPoliciesRepo.save(policy);
        }
    }

    // ── 10. PLANTILLAS ───────────────────────────────────────────────────────────

    private List<Templates> seedTemplates() {
        Templates web = new Templates();
        web.setTemplateKey("TPL_WEB_2025");
        web.setName("Consentimiento Web 2025");
        web.setDescription(
                "Plantilla principal para el centro de preferencias web de MedVida. Incluye todos los propósitos activos.");
        web.setVersion(1);
        web.setTitle("Sus preferencias de privacidad");
        web.setIsActive(true);
        web.setCreatedAt(NOW.minusDays(45).atOffset(java.time.ZoneOffset.UTC));

        Templates app = new Templates();
        app.setTemplateKey("TPL_APP_MOVIL");
        app.setName("Consentimiento App Móvil v1");
        app.setDescription(
                "Plantilla compacta para la app móvil MedVida. Solo propósitos esenciales y de mejora de servicio.");
        app.setVersion(1);
        app.setTitle("Privacidad en MedVida App");
        app.setIsActive(true);
        app.setCreatedAt(NOW.minusDays(45).atOffset(java.time.ZoneOffset.UTC));

        return templatesRepo.saveAll(List.of(web, app));
    }

    // ── 11. PROPÓSITOS POR PLANTILLA ─────────────────────────────────────────────

    private void seedTemplatePurposes(List<Templates> templates, List<Purposes> purposes) {
        Templates web = templates.get(0);
        Templates app = templates.get(1);

        linkTemplatePurpose(web, purposes.get(0), 1); // MKTG_CAMPANAS
        linkTemplatePurpose(web, purposes.get(1), 2); // LEGAL_FACTURACION
        linkTemplatePurpose(web, purposes.get(2), 3); // TI_MEJORA
        linkTemplatePurpose(web, purposes.get(3), 4); // SALUD_DATOS_CLINICOS

        linkTemplatePurpose(app, purposes.get(1), 1); // LEGAL_FACTURACION
        linkTemplatePurpose(app, purposes.get(2), 2); // TI_MEJORA
    }

    private void linkTemplatePurpose(Templates template, Purposes purpose, int order) {
        TemplatePurposes.TemplatePurposesId id = new TemplatePurposes.TemplatePurposesId();
        id.setTemplateId(template.getId());
        id.setPurposeId(purpose.getId());
        TemplatePurposes tp = new TemplatePurposes();
        tp.setId(id);
        tp.setTemplate(template);
        tp.setPurpose(purpose);
        tp.setOrderPosition(order);
        tp.setIsVisible(true);
        templatePurposesRepo.save(tp);
    }

    // ── 12. TITULARES ────────────────────────────────────────────────────────────

    private List<DataSubjects> seedDataSubjects() {
        return dataSubjectsRepo.saveAll(List.of(
                subject("RUT:12345678-9"),
                subject("RUT:98765432-1"),
                subject("RUT:11223344-5"),
                subject("RUT:55667788-3")));
    }

    private DataSubjects subject(String identifier) {
        DataSubjects s = new DataSubjects();
        s.setIdentifier(identifier);
        s.setCreatedAt(NOW.minusDays(60));
        return s;
    }

    // ── 13. DOCUMENTOS DE PRIVACIDAD ─────────────────────────────────────────────

    private List<PrivacyDocuments> seedPrivacyDocuments(List<Templates> templates, List<Users> users) {
        Users dpo = users.get(0);
        Templates web = templates.get(0);

        PrivacyDocuments politica = PrivacyDocuments.builder()
                .templateId(web.getId())
                .category(DocumentCategory.POLITICA_PRIVACIDAD)
                .status(DocumentStatus.PUBLISHED)
                .version(1)
                .name("Política de Privacidad MedVida 2025")
                .content(POLITICA_CONTENT)
                .isActive(true)
                .publishAt(NOW.minusDays(10))
                .createdBy(dpo.getKeycloakId())
                .approvedBy(dpo.getKeycloakId())
                .hashSha256("e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6")
                .build();

        PrivacyDocuments cookies = PrivacyDocuments.builder()
                .category(DocumentCategory.AVISO_COOKIES)
                .status(DocumentStatus.IN_REVIEW)
                .version(1)
                .name("Aviso de Cookies Sitio Web")
                .content(COOKIES_CONTENT)
                .isActive(true)
                .createdBy(dpo.getKeycloakId())
                .build();

        PrivacyDocuments sensibles = PrivacyDocuments.builder()
                .category(DocumentCategory.DATOS_SENSIBLES)
                .status(DocumentStatus.DRAFT)
                .version(1)
                .name("Tratamiento de Datos Sensibles de Salud")
                .content(SENSIBLES_CONTENT)
                .isActive(true)
                .createdBy(dpo.getKeycloakId())
                .build();

        List<PrivacyDocuments> saved = privacyDocsRepo.saveAll(List.of(politica, cookies, sensibles));

        saved.forEach(d -> {
            d.setDocumentFamilyId(d.getId());
            privacyDocsRepo.save(d);
        });

        return saved;
    }

    // ── 14. PROPÓSITOS POR DOCUMENTO ─────────────────────────────────────────────

    private void seedDocumentPurposes(List<PrivacyDocuments> docs, List<Purposes> purposes) {
        PrivacyDocuments politica = docs.get(0);
        PrivacyDocuments sensibles = docs.get(2);

        linkDocPurpose(politica, purposes.get(0));
        linkDocPurpose(politica, purposes.get(1));
        linkDocPurpose(politica, purposes.get(2));

        linkDocPurpose(sensibles, purposes.get(3));
    }

    private void linkDocPurpose(PrivacyDocuments doc, Purposes purpose) {
        DocumentPurposes.DocumentPurposesId id = new DocumentPurposes.DocumentPurposesId();
        id.setDocumentId(doc.getId());
        id.setPurposeId(purpose.getId());
        DocumentPurposes dp = new DocumentPurposes();
        dp.setId(id);
        dp.setDocument(doc);
        dp.setPurpose(purpose);
        dp.setIsActive(true);
        docPurposesRepo.save(dp);
    }

    // ── 15. ACUERDOS DE CONSENTIMIENTO ───────────────────────────────────────────

    private void seedAgreements(List<DataSubjects> subjects, List<Templates> templates,
            List<PrivacyDocuments> docs, List<Purposes> purposes) {
        Templates web = templates.get(0);
        Templates app = templates.get(1);
        PrivacyDocuments politica = docs.get(0);

        Agreements a1 = agreement(subjects.get(0), web, politica, "ACTIVE", NOW.minusDays(8),
                "f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1", null);
        a1 = agreementsRepo.save(a1);
        addAgreementPurpose(a1, purposes.get(0), true, "ART_12_CONSENTIMIENTO");
        addAgreementPurpose(a1, purposes.get(1), true, "ART_12_CONTRATO");
        addAgreementPurpose(a1, purposes.get(2), true, "ART_12_CONTRATO");
        addAgreementPurpose(a1, purposes.get(3), true, "ART_13_DATOS_SENSIBLES");
        addMetadata(a1, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36", "WEB");

        Agreements a2 = agreement(subjects.get(1), app, politica, "ACTIVE", NOW.minusDays(6),
                "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2", null);
        a2 = agreementsRepo.save(a2);
        addAgreementPurpose(a2, purposes.get(1), true, "ART_12_CONTRATO");
        addAgreementPurpose(a2, purposes.get(2), false, "ART_12_CONTRATO");
        addMetadata(a2, "MedVidaApp/2.1 (iPhone; iOS 17.4)", "MOVIL");

        Agreements a3 = agreement(subjects.get(2), web, politica, "REVOKED", NOW.minusDays(30),
                "b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3", null);
        a3 = agreementsRepo.save(a3);
        addAgreementPurpose(a3, purposes.get(0), true, "ART_12_CONSENTIMIENTO");
        addAgreementPurpose(a3, purposes.get(1), true, "ART_12_CONTRATO");
        addMetadata(a3, "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15", "WEB");

        Agreements a4 = agreement(subjects.get(3), web, politica, "ACTIVE", NOW.minusDays(2),
                "c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4", null);
        a4 = agreementsRepo.save(a4);
        addAgreementPurpose(a4, purposes.get(0), false, "ART_12_CONSENTIMIENTO");
        addAgreementPurpose(a4, purposes.get(1), true, "ART_12_CONTRATO");
        addAgreementPurpose(a4, purposes.get(2), false, "ART_12_CONTRATO");
        addAgreementPurpose(a4, purposes.get(3), false, "ART_13_DATOS_SENSIBLES");
        addMetadata(a4, "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36", "WEB");
    }

    private Agreements agreement(DataSubjects subject, Templates template,
            PrivacyDocuments doc, String status,
            LocalDateTime createdAt, String hash, String prevHash) {
        Agreements a = new Agreements();
        a.setDataSubjectId(subject.getId());
        a.setTemplateId(template.getId());
        a.setTemplateVersion(template.getVersion());
        a.setDocumentId(doc.getId());
        a.setStatus(status);
        a.setCreatedAt(createdAt);
        a.setHashSha256(hash);
        a.setPreviousHashSha256(prevHash);
        a.setExpiration(createdAt.plusYears(1));
        return a;
    }

    private void addAgreementPurpose(Agreements agreement, Purposes purpose,
            boolean accepted, String legalBasisCode) {
        AgreementsPurposes ap = new AgreementsPurposes();
        ap.setAgreementId(agreement.getId());
        ap.setPurposeId(purpose.getId());
        ap.setAccepted(accepted);
        ap.setPurposeCode(purpose.getCode());
        ap.setPurposeName(purpose.getName());
        ap.setPurposeDescription(purpose.getDescription());
        ap.setPurposeShortDescription(purpose.getShortDescription());
        ap.setPurposeRequired(purpose.getRequired());
        ap.setPurposeRevocable(purpose.getRevocable());
        ap.setPurposeHash(purpose.getHashSha256());
        ap.setLegalBasisCode(legalBasisCode);
        ap.setCreatedAt(agreement.getCreatedAt());
        agreementsPurposesRepo.save(ap);
    }

    private void addMetadata(Agreements agreement, String userAgent, String channel) {
        AgreementMetadata m = new AgreementMetadata();
        m.setAgreementId(agreement.getId());
        m.setIpOrigin(null);
        m.setUserAgent(userAgent);
        m.setCaptureChannel(channel);
        m.setSignatureToken("test-token-" + agreement.getId().toString().substring(0, 8));
        m.setAuthProvider("KEYCLOAK");
        m.setExtraVariables("{\"env\":\"test\"}");
        m.setCreatedAt(agreement.getCreatedAt());
        agreementMetadataRepo.save(m);
    }

    // ── CONTENIDOS DE EJEMPLO PARA DOCUMENTOS ────────────────────────────────────

    private static final String POLITICA_CONTENT = """
            POLÍTICA DE PRIVACIDAD — MEDVIDA SpA
            Última actualización: 1 de junio de 2025

            1. IDENTIDAD DEL RESPONSABLE
            MedVida SpA, RUT 76.543.210-K, con domicilio en Av. Providencia 1234, Santiago, Chile,
            es el responsable del tratamiento de sus datos personales.

            2. DATOS QUE TRATAMOS
            Tratamos su nombre completo, RUT, correo electrónico, teléfono, datos de salud y
            datos financieros, según los propósitos descritos en este documento.

            3. BASE LEGAL
            El tratamiento se realiza conforme a los artículos 12, 13 y 16 de la Ley N° 21.719
            sobre protección de datos personales.

            4. DERECHOS DEL TITULAR
            Ud. tiene derecho a acceder, rectificar, cancelar y oponerse al tratamiento de sus datos,
            enviando un correo a privacidad@medvida.cl.
            """;

    private static final String COOKIES_CONTENT = """
            AVISO DE COOKIES — MEDVIDA SpA
            Última actualización: 1 de junio de 2025

            Nuestro sitio web utiliza cookies técnicas (esenciales para el funcionamiento)
            y cookies analíticas (para medir el uso del sitio, con su consentimiento).

            Puede gestionar sus preferencias en cualquier momento desde el
            centro de privacidad o la configuración de su navegador.
            """;

    private static final String SENSIBLES_CONTENT = """
            TRATAMIENTO DE DATOS SENSIBLES DE SALUD — MEDVIDA SpA
            Última actualización: 1 de junio de 2025

            De conformidad con el artículo 13 de la Ley N° 21.719, MedVida SpA trata datos
            sensibles de salud (diagnósticos, medicamentos, historial clínico) exclusivamente
            para la gestión de coberturas y reembolsos de su plan de salud complementario.

            Este tratamiento requiere su consentimiento explícito e informado.
            Puede revocarlo en cualquier momento; la revocación no afecta la licitud del
            tratamiento previo a su ejercicio.
            """;
}
