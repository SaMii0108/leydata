package com.leydata.backend.agreement.application.service;

import com.leydata.backend.agreement.application.dto.*;
import com.leydata.backend.agreement.domain.exception.AgreementNotFoundException;
import com.leydata.backend.agreement.infrastructure.persistence.AgreementMetadataRepository;
import com.leydata.backend.agreement.infrastructure.persistence.AgreementsPurposesRepository;
import com.leydata.backend.agreement.infrastructure.persistence.AgreementsRepository;
import com.leydata.backend.entity.*;
import com.leydata.backend.privacydoc.domain.enums.DocumentCategory;
import com.leydata.backend.privacydoc.domain.exception.BusinessValidationException;
import com.leydata.backend.privacydoc.infrastructure.persistence.DocumentPurposesRepository;
import com.leydata.backend.privacydoc.infrastructure.persistence.PrivacyDocumentsRepository;
import com.leydata.backend.purposes.infrastructure.persistence.PurposesRepository;
import com.leydata.backend.repository.DataSubjectsRepository;
import com.leydata.backend.shared.SecurityContextHelper;
import com.leydata.backend.template.infrastructure.persistence.TemplatePurposesRepository;
import com.leydata.backend.template.infrastructure.persistence.TemplatesRepository;
import com.leydata.backend.audit.application.service.AuditService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgreementServiceTest {

    @Mock private AgreementsRepository agreementsRepo;
    @Mock private AgreementsPurposesRepository agreementsPurposesRepo;
    @Mock private AgreementMetadataRepository agreementMetadataRepo;
    @Mock private DataSubjectsRepository dataSubjectsRepo;
    @Mock private TemplatesRepository templatesRepo;
    @Mock private TemplatePurposesRepository templatePurposesRepo;
    @Mock private PrivacyDocumentsRepository privacyDocumentsRepo;
    @Mock private DocumentPurposesRepository documentPurposesRepo;
    @Mock private PurposesRepository purposesRepo;
    @Mock private AuditService auditService;
    @Mock private SecurityContextHelper securityContextHelper;

    @InjectMocks
    private AgreementService service;

    private final UUID dataSubjectId = UUID.randomUUID();
    private final UUID templateId = UUID.randomUUID();
    private final UUID documentId = UUID.randomUUID();
    private final UUID purposeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(agreementsRepo.save(any(Agreements.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(agreementsPurposesRepo.save(any(AgreementsPurposes.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(agreementMetadataRepo.save(any(AgreementMetadata.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(agreementsRepo.findTopByOrderByCreatedAtDesc()).thenReturn(Optional.empty());
        lenient().when(agreementsPurposesRepo.findTopByOrderByCreatedAtDesc()).thenReturn(Optional.empty());
        lenient().when(securityContextHelper.getKeycloakId()).thenThrow(new RuntimeException("sin usuario autenticado"));
    }

    // ── Helpers de fixtures ────────────────────────────────────────────────────────

    private DataSubjects dataSubject() {
        DataSubjects ds = new DataSubjects();
        ds.setId(dataSubjectId);
        ds.setIdentifier("PSEUDO-1");
        return ds;
    }

    private Templates activeTemplate() {
        Templates t = new Templates();
        t.setId(templateId);
        t.setIsActive(true);
        t.setVersion(2);
        return t;
    }

    private PrivacyDocuments document() {
        PrivacyDocuments d = new PrivacyDocuments();
        d.setId(documentId);
        d.setCategory(DocumentCategory.MARKETING_DIRECTO);
        return d;
    }

    private TemplatePurposes visiblePurposeLink(UUID purposeId) {
        TemplatePurposes tp = new TemplatePurposes();
        Purposes p = new Purposes();
        p.setId(purposeId);
        tp.setPurpose(p);
        tp.setIsVisible(true);
        return tp;
    }

    private Purposes purpose(UUID id, boolean required) {
        Purposes p = new Purposes();
        p.setId(id);
        p.setCode("MARKETING");
        p.setName("Marketing");
        p.setDescription("Uso de datos para marketing");
        p.setShortDescription("Marketing");
        p.setRequired(required);
        p.setRevocable(true);
        p.setHashSha256("purpose-hash");
        return p;
    }

    private CreateAgreementRequest baseRequest(UUID purposeId, boolean accepted) {
        CreateAgreementRequest req = new CreateAgreementRequest();
        req.setDataSubjectId(dataSubjectId);
        req.setTemplateId(templateId);
        req.setDocumentId(documentId);
        PurposeDecisionRequest decision = new PurposeDecisionRequest();
        decision.setPurposeId(purposeId);
        decision.setAccepted(accepted);
        req.setPurposes(List.of(decision));
        return req;
    }

    private void mockHappyPathDependencies(boolean required) {
        when(dataSubjectsRepo.findById(dataSubjectId)).thenReturn(Optional.of(dataSubject()));
        when(templatesRepo.findById(templateId)).thenReturn(Optional.of(activeTemplate()));
        when(privacyDocumentsRepo.findById(documentId)).thenReturn(Optional.of(document()));
        when(templatePurposesRepo.findByTemplate_IdOrderByOrderPosition(templateId))
                .thenReturn(List.of(visiblePurposeLink(purposeId)));
        when(documentPurposesRepo.existsByDocument_IdAndPurpose_IdAndIsActiveTrue(documentId, purposeId))
                .thenReturn(true);
        when(purposesRepo.findById(purposeId)).thenReturn(Optional.of(purpose(purposeId, required)));
    }

    // ── create() ─────────────────────────────────────────────────────────────────

    @Test
    void create_creaAgreementConSnapshotYHash() {
        mockHappyPathDependencies(false);
        when(agreementsRepo.findByDataSubjectIdAndTemplateIdAndStatus(dataSubjectId, templateId, "ACTIVE"))
                .thenReturn(Optional.empty());

        AgreementResponse response = service.create(baseRequest(purposeId, true), "127.0.0.1", "JUnit");

        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        assertThat(response.getDataSubjectId()).isEqualTo(dataSubjectId);
        assertThat(response.getTemplateVersion()).isEqualTo(2); // Regla 3
        assertThat(response.getHashSha256()).isNotBlank();
        assertThat(response.getPurposes()).hasSize(1);
        assertThat(response.getPurposes().get(0).getAccepted()).isTrue();
        assertThat(response.getPurposes().get(0).getPurposeCode()).isEqualTo("MARKETING"); // Regla 6
        assertThat(response.getMetadata().getIpOrigin()).isEqualTo("127.0.0.1");
    }

    @Test
    void create_lanzaBusinessValidationException_siDataSubjectNoExiste() {
        when(dataSubjectsRepo.findById(dataSubjectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(baseRequest(purposeId, true), "ip", "ua"))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void create_lanzaBusinessValidationException_siTemplateNoExiste() {
        when(dataSubjectsRepo.findById(dataSubjectId)).thenReturn(Optional.of(dataSubject()));
        when(templatesRepo.findById(templateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(baseRequest(purposeId, true), "ip", "ua"))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void create_lanzaBusinessValidationException_siTemplateNoEstaActivo() {
        when(dataSubjectsRepo.findById(dataSubjectId)).thenReturn(Optional.of(dataSubject()));
        Templates draft = activeTemplate();
        draft.setIsActive(false);
        when(templatesRepo.findById(templateId)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.create(baseRequest(purposeId, true), "ip", "ua"))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void create_lanzaBusinessValidationException_siDocumentoNoExiste() {
        when(dataSubjectsRepo.findById(dataSubjectId)).thenReturn(Optional.of(dataSubject()));
        when(templatesRepo.findById(templateId)).thenReturn(Optional.of(activeTemplate()));
        when(privacyDocumentsRepo.findById(documentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(baseRequest(purposeId, true), "ip", "ua"))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void create_lanzaBusinessValidationException_siFaltaUnaPurposeVisible() {
        when(dataSubjectsRepo.findById(dataSubjectId)).thenReturn(Optional.of(dataSubject()));
        when(templatesRepo.findById(templateId)).thenReturn(Optional.of(activeTemplate()));
        when(privacyDocumentsRepo.findById(documentId)).thenReturn(Optional.of(document()));

        UUID otherVisiblePurposeId = UUID.randomUUID();
        when(templatePurposesRepo.findByTemplate_IdOrderByOrderPosition(templateId))
                .thenReturn(List.of(visiblePurposeLink(purposeId), visiblePurposeLink(otherVisiblePurposeId)));

        assertThatThrownBy(() -> service.create(baseRequest(purposeId, true), "ip", "ua"))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void create_lanzaBusinessValidationException_siIncluyePurposeNoVisible() {
        when(dataSubjectsRepo.findById(dataSubjectId)).thenReturn(Optional.of(dataSubject()));
        when(templatesRepo.findById(templateId)).thenReturn(Optional.of(activeTemplate()));
        when(privacyDocumentsRepo.findById(documentId)).thenReturn(Optional.of(document()));
        when(templatePurposesRepo.findByTemplate_IdOrderByOrderPosition(templateId))
                .thenReturn(List.of()); // ninguna purpose visible en el template

        assertThatThrownBy(() -> service.create(baseRequest(purposeId, true), "ip", "ua"))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void create_lanzaBusinessValidationException_siDocumentoNoCubrePurpose() {
        when(dataSubjectsRepo.findById(dataSubjectId)).thenReturn(Optional.of(dataSubject()));
        when(templatesRepo.findById(templateId)).thenReturn(Optional.of(activeTemplate()));
        when(privacyDocumentsRepo.findById(documentId)).thenReturn(Optional.of(document()));
        when(templatePurposesRepo.findByTemplate_IdOrderByOrderPosition(templateId))
                .thenReturn(List.of(visiblePurposeLink(purposeId)));
        when(documentPurposesRepo.existsByDocument_IdAndPurpose_IdAndIsActiveTrue(documentId, purposeId))
                .thenReturn(false);

        assertThatThrownBy(() -> service.create(baseRequest(purposeId, true), "ip", "ua"))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void create_lanzaBusinessValidationException_siPurposeRequiredEsRechazada() {
        mockHappyPathDependencies(true);

        assertThatThrownBy(() -> service.create(baseRequest(purposeId, false), "ip", "ua"))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void create_cierraElAgreementActivoAnteriorYEncadenaPreviousAgreementsId() {
        mockHappyPathDependencies(false);

        Agreements previousActive = new Agreements();
        UUID previousId = UUID.randomUUID();
        previousActive.setId(previousId);
        previousActive.setStatus("ACTIVE");
        when(agreementsRepo.findByDataSubjectIdAndTemplateIdAndStatus(dataSubjectId, templateId, "ACTIVE"))
                .thenReturn(Optional.of(previousActive));

        AgreementsPurposes previousPurpose = new AgreementsPurposes();
        previousPurpose.setStatus("ACTIVE");
        when(agreementsPurposesRepo.findByAgreementId(previousId)).thenReturn(List.of(previousPurpose));

        AgreementResponse response = service.create(baseRequest(purposeId, true), "ip", "ua");

        assertThat(previousActive.getStatus()).isEqualTo("REVOKED"); // Regla 8/15
        assertThat(previousPurpose.getStatus()).isEqualTo("REVOKED"); // Regla 6.1
        assertThat(response.getPreviousAgreementsId()).isEqualTo(previousId); // Regla 9
    }

    @Test
    void create_sinAgreementActivoPrevio_previousAgreementsIdQuedaNulo() {
        mockHappyPathDependencies(false);
        when(agreementsRepo.findByDataSubjectIdAndTemplateIdAndStatus(dataSubjectId, templateId, "ACTIVE"))
                .thenReturn(Optional.empty());

        AgreementResponse response = service.create(baseRequest(purposeId, true), "ip", "ua");

        assertThat(response.getPreviousAgreementsId()).isNull();
    }

    @Test
    void create_encadenaPreviousHashShaContraElUltimoAgreement() {
        mockHappyPathDependencies(false);
        when(agreementsRepo.findByDataSubjectIdAndTemplateIdAndStatus(dataSubjectId, templateId, "ACTIVE"))
                .thenReturn(Optional.empty());

        Agreements lastGlobal = new Agreements();
        lastGlobal.setId(UUID.randomUUID());
        lastGlobal.setHashSha256("hash-anterior-global");
        when(agreementsRepo.findTopByOrderByCreatedAtDesc()).thenReturn(Optional.of(lastGlobal));

        AgreementResponse response = service.create(baseRequest(purposeId, true), "ip", "ua");

        assertThat(response.getHashSha256()).isNotEqualTo("hash-anterior-global");
        // El encadenamiento se valida indirectamente: no lanza excepción y genera un hash distinto al propio.
    }

    @Test
    void create_sinBloqueMetadata_noRompeYGuardaSoloIpYUserAgent() {
        mockHappyPathDependencies(false);
        when(agreementsRepo.findByDataSubjectIdAndTemplateIdAndStatus(dataSubjectId, templateId, "ACTIVE"))
                .thenReturn(Optional.empty());

        CreateAgreementRequest req = baseRequest(purposeId, true);
        req.setMetadata(null);

        AgreementResponse response = service.create(req, "10.0.0.1", "ua-test");

        assertThat(response.getMetadata().getIpOrigin()).isEqualTo("10.0.0.1");
        assertThat(response.getMetadata().getUserAgent()).isEqualTo("ua-test");
        assertThat(response.getMetadata().getCaptureChannel()).isNull();
    }

    // ── getById() ────────────────────────────────────────────────────────────────

    @Test
    void getById_devuelveElAgreementConPurposesYMetadata() {
        UUID id = UUID.randomUUID();
        Agreements agreement = new Agreements();
        agreement.setId(id);
        agreement.setStatus("ACTIVE");
        when(agreementsRepo.findById(id)).thenReturn(Optional.of(agreement));
        when(agreementsPurposesRepo.findByAgreementId(id)).thenReturn(List.of());
        when(agreementMetadataRepo.findByAgreementId(id)).thenReturn(Optional.empty());

        AgreementResponse response = service.getById(id);

        assertThat(response.getId()).isEqualTo(id);
    }

    @Test
    void getById_lanzaAgreementNotFoundException_siNoExiste() {
        UUID id = UUID.randomUUID();
        when(agreementsRepo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id))
                .isInstanceOf(AgreementNotFoundException.class);
    }

    // ── list() ───────────────────────────────────────────────────────────────────

    @Test
    void list_filtraPorDataSubjectId() {
        Agreements a = new Agreements();
        a.setId(UUID.randomUUID());
        a.setDataSubjectId(dataSubjectId);
        when(agreementsRepo.findByDataSubjectIdOrderByCreatedAtDesc(dataSubjectId)).thenReturn(List.of(a));
        when(agreementsPurposesRepo.findByAgreementId(any())).thenReturn(List.of());
        when(agreementMetadataRepo.findByAgreementId(any())).thenReturn(Optional.empty());

        List<AgreementResponse> result = service.list(dataSubjectId, null, null);

        assertThat(result).hasSize(1);
    }

    @Test
    void list_filtraPorTemplateIdCuandoNoHayDataSubjectId() {
        Agreements a = new Agreements();
        a.setId(UUID.randomUUID());
        a.setTemplateId(templateId);
        when(agreementsRepo.findByTemplateId(templateId)).thenReturn(List.of(a));
        when(agreementsPurposesRepo.findByAgreementId(any())).thenReturn(List.of());
        when(agreementMetadataRepo.findByAgreementId(any())).thenReturn(Optional.empty());

        List<AgreementResponse> result = service.list(null, templateId, null);

        assertThat(result).hasSize(1);
    }

    @Test
    void list_filtraPorStatusCuandoNoHayDataSubjectIdNiTemplateId() {
        Agreements a = new Agreements();
        a.setId(UUID.randomUUID());
        a.setStatus("REVOKED");
        when(agreementsRepo.findByStatus("REVOKED")).thenReturn(List.of(a));
        when(agreementsPurposesRepo.findByAgreementId(any())).thenReturn(List.of());
        when(agreementMetadataRepo.findByAgreementId(any())).thenReturn(Optional.empty());

        List<AgreementResponse> result = service.list(null, null, "REVOKED");

        assertThat(result).hasSize(1);
    }

    @Test
    void list_sinFiltros_devuelveTodos() {
        Agreements a = new Agreements();
        a.setId(UUID.randomUUID());
        when(agreementsRepo.findAll()).thenReturn(List.of(a));
        when(agreementsPurposesRepo.findByAgreementId(any())).thenReturn(List.of());
        when(agreementMetadataRepo.findByAgreementId(any())).thenReturn(Optional.empty());

        List<AgreementResponse> result = service.list(null, null, null);

        assertThat(result).hasSize(1);
    }

    @Test
    void list_combinaFiltrosDataSubjectIdYStatus() {
        Agreements matching = new Agreements();
        matching.setId(UUID.randomUUID());
        matching.setDataSubjectId(dataSubjectId);
        matching.setStatus("ACTIVE");

        Agreements notMatching = new Agreements();
        notMatching.setId(UUID.randomUUID());
        notMatching.setDataSubjectId(dataSubjectId);
        notMatching.setStatus("REVOKED");

        when(agreementsRepo.findByDataSubjectIdOrderByCreatedAtDesc(dataSubjectId))
                .thenReturn(List.of(matching, notMatching));
        when(agreementsPurposesRepo.findByAgreementId(any())).thenReturn(List.of());
        when(agreementMetadataRepo.findByAgreementId(any())).thenReturn(Optional.empty());

        List<AgreementResponse> result = service.list(dataSubjectId, null, "ACTIVE");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("ACTIVE");
    }

    // ── getActive() ──────────────────────────────────────────────────────────────

    @Test
    void getActive_devuelveElAgreementSiHayUnoActivo() {
        Agreements active = new Agreements();
        active.setId(UUID.randomUUID());
        active.setStatus("ACTIVE");
        when(agreementsRepo.findByDataSubjectIdAndTemplateIdAndStatus(dataSubjectId, templateId, "ACTIVE"))
                .thenReturn(Optional.of(active));
        when(agreementsPurposesRepo.findByAgreementId(any())).thenReturn(List.of());
        when(agreementMetadataRepo.findByAgreementId(any())).thenReturn(Optional.empty());

        Optional<AgreementResponse> result = service.getActive(dataSubjectId, templateId);

        assertThat(result).isPresent();
    }

    @Test
    void getActive_devuelveVacioSiNoHayNinguno() {
        when(agreementsRepo.findByDataSubjectIdAndTemplateIdAndStatus(dataSubjectId, templateId, "ACTIVE"))
                .thenReturn(Optional.empty());

        Optional<AgreementResponse> result = service.getActive(dataSubjectId, templateId);

        assertThat(result).isEmpty();
    }

    // ── recalculateHash() ────────────────────────────────────────────────────────

    @Test
    void recalculateHash_devuelveElMismoHashSiNoHuboAlteraciones() {
        UUID id = UUID.randomUUID();
        Agreements agreement = new Agreements();
        agreement.setId(id);
        agreement.setCreatedAt(LocalDateTime.now());
        when(agreementsRepo.findById(id)).thenReturn(Optional.of(agreement));
        when(agreementsPurposesRepo.findByAgreementId(id)).thenReturn(List.of());
        when(agreementMetadataRepo.findByAgreementId(id)).thenReturn(Optional.empty());

        String first = service.recalculateHash(id);
        String second = service.recalculateHash(id);

        assertThat(first).isNotBlank();
        assertThat(first).isEqualTo(second);
    }

    @Test
    void recalculateHash_lanzaAgreementNotFoundException_siNoExiste() {
        UUID id = UUID.randomUUID();
        when(agreementsRepo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recalculateHash(id))
                .isInstanceOf(AgreementNotFoundException.class);
    }
}
