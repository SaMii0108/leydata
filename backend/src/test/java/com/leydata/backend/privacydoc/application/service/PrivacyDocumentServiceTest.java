package com.leydata.backend.privacydoc.application.service;

import com.leydata.backend.audit.application.service.AuditService;
import com.leydata.backend.entity.DocumentPurposes;
import com.leydata.backend.entity.Domains;
import com.leydata.backend.entity.PrivacyDocuments;
import com.leydata.backend.entity.PurposeRequests;
import com.leydata.backend.entity.Purposes;
import com.leydata.backend.entity.Templates;
import com.leydata.backend.notification.application.service.NotificationService;
import com.leydata.backend.orgdomain.infrastructure.persistence.DomainsRepository;
import com.leydata.backend.privacydoc.application.dto.CreateDocumentRequest;
import com.leydata.backend.privacydoc.application.dto.PrivacyDocumentResponse;
import com.leydata.backend.privacydoc.application.dto.RejectDocumentRequest;
import com.leydata.backend.privacydoc.application.dto.UpdateDocumentRequest;
import com.leydata.backend.privacydoc.application.dto.VerifyResponse;
import com.leydata.backend.privacydoc.domain.enums.DocumentCategory;
import com.leydata.backend.privacydoc.domain.enums.DocumentStatus;
import com.leydata.backend.privacydoc.domain.exception.BusinessValidationException;
import com.leydata.backend.privacydoc.domain.exception.DocumentNotFoundException;
import com.leydata.backend.privacydoc.domain.exception.InvalidTransitionException;
import com.leydata.backend.privacydoc.infrastructure.pdf.PdfGeneratorService;
import com.leydata.backend.privacydoc.infrastructure.persistence.DocumentPurposesRepository;
import com.leydata.backend.privacydoc.infrastructure.persistence.PrivacyDocumentsRepository;
import com.leydata.backend.purposerequest.infrastructure.persistence.PurposeRequestsRepository;
import com.leydata.backend.purposes.infrastructure.persistence.PurposesRepository;
import com.leydata.backend.shared.SecurityContextHelper;
import com.leydata.backend.template.infrastructure.persistence.TemplatesRepository;
import com.leydata.backend.userdomain.infrastructure.persistence.UserDomainRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrivacyDocumentServiceTest {

    @Mock private PrivacyDocumentsRepository documentRepo;
    @Mock private DocumentPurposesRepository purposeRepo;
    @Mock private DomainsRepository domainsRepo;
    @Mock private PurposesRepository purposesRepo;
    @Mock private PurposeRequestsRepository purposeRequestsRepo;
    @Mock private TemplatesRepository templatesRepo;
    @Mock private PdfGeneratorService pdfGenerator;
    @Mock private NotificationService notificationService;
    @Mock private UserDomainRepository userDomainRepository;
    @Mock private AuditService auditService;
    @Mock private SecurityContextHelper securityContextHelper;

    @InjectMocks
    private PrivacyDocumentService service;

    private final UUID documentId = UUID.randomUUID();
    private final UUID templateId = UUID.randomUUID();
    private final UUID purposeId = UUID.randomUUID();
    private final UUID domainId = UUID.randomUUID();
    private final String actorId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        lenient().when(securityContextHelper.getKeycloakId()).thenReturn(actorId);
        lenient().when(securityContextHelper.getName()).thenReturn("DPO Test");
        lenient().when(securityContextHelper.getActorRole()).thenReturn("DPO");
        lenient().when(documentRepo.save(any(PrivacyDocuments.class))).thenAnswer(inv -> {
            PrivacyDocuments d = inv.getArgument(0);
            if (d.getId() == null) d.setId(documentId);
            return d;
        });
        lenient().when(userDomainRepository.findByDomain_Id(any())).thenReturn(List.of());
        asPrivilegedUser();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void asPrivilegedUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("dpo", null, List.of(new SimpleGrantedAuthority("ROLE_DPO"))));
    }

    private void asPublicUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("titular", null, List.of(new SimpleGrantedAuthority("ROLE_TITULAR"))));
    }

    private PrivacyDocuments document(DocumentStatus status) {
        return PrivacyDocuments.builder()
                .id(documentId)
                .documentFamilyId(documentId)
                .templateId(templateId)
                .category(DocumentCategory.MARKETING_DIRECTO)
                .status(status)
                .version(1)
                .name("Política de Marketing")
                .content("Contenido legal")
                .isActive(true)
                .build();
    }

    private Templates activeTemplate() {
        Templates t = new Templates();
        t.setId(templateId);
        t.setIsActive(true);
        return t;
    }

    private Purposes approvedActivePurpose() {
        Purposes p = new Purposes();
        p.setId(purposeId);
        p.setName("Marketing");
        p.setDomainId(domainId);
        p.setIsActive(true);
        p.setApprovedBy("dpo-id");
        return p;
    }

    private DocumentPurposes activeLink(PrivacyDocuments doc, Purposes purpose) {
        DocumentPurposes link = new DocumentPurposes();
        link.setId(new DocumentPurposes.DocumentPurposesId(doc.getId(), purpose.getId()));
        link.setDocument(doc);
        link.setPurpose(purpose);
        link.setIsActive(true);
        return link;
    }

    // ── create() ─────────────────────────────────────────────────────────────────

    @Test
    void create_creaDocumentoDraftVersion1ConFamilyIdPropio() {
        CreateDocumentRequest req = new CreateDocumentRequest();
        req.setCategory(DocumentCategory.MARKETING_DIRECTO);
        req.setName("Política de Marketing");

        PrivacyDocumentResponse response = service.create(req);

        assertThat(response.getStatus()).isEqualTo(DocumentStatus.DRAFT);
        assertThat(response.getVersion()).isEqualTo(1);
        assertThat(response.getDocumentFamilyId()).isEqualTo(documentId);
    }

    @Test
    void create_lanzaExcepcion_siLaTemplateNoEstaActiva() {
        Templates inactive = activeTemplate();
        inactive.setIsActive(false);
        when(templatesRepo.findById(templateId)).thenReturn(Optional.of(inactive));

        CreateDocumentRequest req = new CreateDocumentRequest();
        req.setCategory(DocumentCategory.MARKETING_DIRECTO);
        req.setName("Doc");
        req.setTemplateId(templateId);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessValidationException.class);
    }

    // ── getById() ────────────────────────────────────────────────────────────────

    @Test
    void getById_usuarioPrivilegiado_veCualquierDocumento() {
        when(documentRepo.findById(documentId)).thenReturn(Optional.of(document(DocumentStatus.DRAFT)));

        PrivacyDocumentResponse response = service.getById(documentId);

        assertThat(response.getStatus()).isEqualTo(DocumentStatus.DRAFT);
    }

    @Test
    void getById_usuarioPublico_documentoNoPublicado_lanzaAccessDeniedException() {
        asPublicUser();
        when(documentRepo.findById(documentId)).thenReturn(Optional.of(document(DocumentStatus.DRAFT)));

        assertThatThrownBy(() -> service.getById(documentId))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void getById_usuarioPublico_documentoPublicado_devuelveVersionPublica() {
        asPublicUser();
        when(documentRepo.findById(documentId)).thenReturn(Optional.of(document(DocumentStatus.PUBLISHED)));

        PrivacyDocumentResponse response = service.getById(documentId);

        assertThat(response.getStatus()).isEqualTo(DocumentStatus.PUBLISHED);
    }

    // ── update() ─────────────────────────────────────────────────────────────────

    @Test
    void update_actualizaCamposEnDraft() {
        when(documentRepo.findById(documentId)).thenReturn(Optional.of(document(DocumentStatus.DRAFT)));

        UpdateDocumentRequest req = new UpdateDocumentRequest();
        req.setName("Nuevo nombre");

        PrivacyDocumentResponse response = service.update(documentId, req);

        assertThat(response.getName()).isEqualTo("Nuevo nombre");
    }

    @Test
    void update_lanzaExcepcion_siNoEstaEnDraft() {
        when(documentRepo.findById(documentId)).thenReturn(Optional.of(document(DocumentStatus.IN_REVIEW)));

        assertThatThrownBy(() -> service.update(documentId, new UpdateDocumentRequest()))
                .isInstanceOf(BusinessValidationException.class);
    }

    // ── deactivate() ─────────────────────────────────────────────────────────────

    @Test
    void deactivate_desactivaSiNoTienePurposesActivas() {
        when(documentRepo.findById(documentId)).thenReturn(Optional.of(document(DocumentStatus.DRAFT)));
        when(purposeRepo.existsByDocument_IdAndIsActiveTrue(documentId)).thenReturn(false);

        PrivacyDocumentResponse response = service.deactivate(documentId);

        assertThat(response.getIsActive()).isFalse();
    }

    @Test
    void deactivate_lanzaExcepcion_siTienePurposesActivas() {
        when(documentRepo.findById(documentId)).thenReturn(Optional.of(document(DocumentStatus.DRAFT)));
        when(purposeRepo.existsByDocument_IdAndIsActiveTrue(documentId)).thenReturn(true);

        assertThatThrownBy(() -> service.deactivate(documentId))
                .isInstanceOf(BusinessValidationException.class);
    }

    // ── addPurpose() ─────────────────────────────────────────────────────────────

    @Test
    void addPurpose_vinculaNuevaPurposeAlDocumentoEnDraft() {
        when(documentRepo.findById(documentId)).thenReturn(Optional.of(document(DocumentStatus.DRAFT)));
        when(purposeRepo.existsByDocument_IdAndPurpose_IdAndIsActiveTrue(documentId, purposeId)).thenReturn(false);
        when(purposesRepo.findById(purposeId)).thenReturn(Optional.of(approvedActivePurpose()));
        when(purposeRepo.findByDocument_IdAndPurpose_Id(documentId, purposeId)).thenReturn(Optional.empty());

        service.addPurpose(documentId, purposeId);

        verify(purposeRepo).save(any(DocumentPurposes.class));
    }

    @Test
    void addPurpose_reactivaVinculoSoftDeletedExistente() {
        PrivacyDocuments doc = document(DocumentStatus.DRAFT);
        DocumentPurposes inactiveLink = activeLink(doc, approvedActivePurpose());
        inactiveLink.setIsActive(false);

        when(documentRepo.findById(documentId)).thenReturn(Optional.of(doc));
        when(purposeRepo.existsByDocument_IdAndPurpose_IdAndIsActiveTrue(documentId, purposeId)).thenReturn(false);
        when(purposesRepo.findById(purposeId)).thenReturn(Optional.of(approvedActivePurpose()));
        when(purposeRepo.findByDocument_IdAndPurpose_Id(documentId, purposeId)).thenReturn(Optional.of(inactiveLink));

        service.addPurpose(documentId, purposeId);

        assertThat(inactiveLink.getIsActive()).isTrue();
        verify(purposeRepo).save(inactiveLink);
    }

    @Test
    void addPurpose_lanzaExcepcion_siYaEstaVinculadaActivamente() {
        when(documentRepo.findById(documentId)).thenReturn(Optional.of(document(DocumentStatus.DRAFT)));
        when(purposeRepo.existsByDocument_IdAndPurpose_IdAndIsActiveTrue(documentId, purposeId)).thenReturn(true);

        assertThatThrownBy(() -> service.addPurpose(documentId, purposeId))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void addPurpose_lanzaExcepcion_siLaPurposeNoEstaAprobadaOActiva() {
        when(documentRepo.findById(documentId)).thenReturn(Optional.of(document(DocumentStatus.DRAFT)));
        when(purposeRepo.existsByDocument_IdAndPurpose_IdAndIsActiveTrue(documentId, purposeId)).thenReturn(false);
        Purposes notApproved = approvedActivePurpose();
        notApproved.setApprovedBy(null);
        when(purposesRepo.findById(purposeId)).thenReturn(Optional.of(notApproved));

        assertThatThrownBy(() -> service.addPurpose(documentId, purposeId))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void addPurpose_lanzaExcepcion_siElDocumentoNoEstaEnDraft() {
        when(documentRepo.findById(documentId)).thenReturn(Optional.of(document(DocumentStatus.APPROVED)));

        assertThatThrownBy(() -> service.addPurpose(documentId, purposeId))
                .isInstanceOf(BusinessValidationException.class);
    }

    // ── removePurpose() ──────────────────────────────────────────────────────────

    @Test
    void removePurpose_desvinculaConSoftDelete() {
        PrivacyDocuments doc = document(DocumentStatus.DRAFT);
        DocumentPurposes link = activeLink(doc, approvedActivePurpose());
        when(documentRepo.findById(documentId)).thenReturn(Optional.of(doc));
        when(purposeRepo.findByDocument_IdAndPurpose_Id(documentId, purposeId)).thenReturn(Optional.of(link));

        service.removePurpose(documentId, purposeId);

        assertThat(link.getIsActive()).isFalse();
    }

    @Test
    void removePurpose_lanzaExcepcion_siNoHayVinculoActivo() {
        when(documentRepo.findById(documentId)).thenReturn(Optional.of(document(DocumentStatus.DRAFT)));
        when(purposeRepo.findByDocument_IdAndPurpose_Id(documentId, purposeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removePurpose(documentId, purposeId))
                .isInstanceOf(BusinessValidationException.class);
    }

    // ── submit() ─────────────────────────────────────────────────────────────────

    @Test
    void submit_draftListo_pasaAInReview() {
        PrivacyDocuments doc = document(DocumentStatus.DRAFT);
        when(documentRepo.findById(documentId)).thenReturn(Optional.of(doc));
        when(purposeRepo.findByDocument_IdAndIsActiveTrue(documentId))
                .thenReturn(List.of(activeLink(doc, approvedActivePurpose())));

        PrivacyDocumentResponse response = service.submit(documentId);

        assertThat(response.getStatus()).isEqualTo(DocumentStatus.IN_REVIEW);
    }

    @Test
    void submit_lanzaExcepcion_siNoEstaEnDraft() {
        when(documentRepo.findById(documentId)).thenReturn(Optional.of(document(DocumentStatus.APPROVED)));

        assertThatThrownBy(() -> service.submit(documentId))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void submit_lanzaExcepcion_siElContenidoEstaVacio() {
        PrivacyDocuments doc = document(DocumentStatus.DRAFT);
        doc.setContent("");
        when(documentRepo.findById(documentId)).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> service.submit(documentId))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void submit_lanzaExcepcion_siNoTieneTemplateAsignada() {
        PrivacyDocuments doc = document(DocumentStatus.DRAFT);
        doc.setTemplateId(null);
        when(documentRepo.findById(documentId)).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> service.submit(documentId))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void submit_lanzaExcepcion_siNoTienePurposesActivasVinculadas() {
        PrivacyDocuments doc = document(DocumentStatus.DRAFT);
        when(documentRepo.findById(documentId)).thenReturn(Optional.of(doc));
        when(purposeRepo.findByDocument_IdAndIsActiveTrue(documentId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.submit(documentId))
                .isInstanceOf(BusinessValidationException.class);
    }

    // ── resubmit() ───────────────────────────────────────────────────────────────

    @Test
    void resubmit_rejectedListo_pasaAInReviewYLimpiaElMotivoDeRechazo() {
        PrivacyDocuments doc = document(DocumentStatus.REJECTED);
        doc.setRejectionReason("Motivo anterior");
        when(documentRepo.findById(documentId)).thenReturn(Optional.of(doc));
        when(purposeRepo.findByDocument_IdAndIsActiveTrue(documentId))
                .thenReturn(List.of(activeLink(doc, approvedActivePurpose())));

        PrivacyDocumentResponse response = service.resubmit(documentId);

        assertThat(response.getStatus()).isEqualTo(DocumentStatus.IN_REVIEW);
        assertThat(response.getRejectionReason()).isNull();
    }

    @Test
    void resubmit_lanzaExcepcion_siNoEstaEnRejected() {
        when(documentRepo.findById(documentId)).thenReturn(Optional.of(document(DocumentStatus.DRAFT)));

        assertThatThrownBy(() -> service.resubmit(documentId))
                .isInstanceOf(BusinessValidationException.class);
    }

    // ── approve() ────────────────────────────────────────────────────────────────

    @Test
    void approve_inReview_pasaAApprovedYSeteaApprovedBy() {
        when(documentRepo.findById(documentId)).thenReturn(Optional.of(document(DocumentStatus.IN_REVIEW)));

        PrivacyDocumentResponse response = service.approve(documentId);

        assertThat(response.getStatus()).isEqualTo(DocumentStatus.APPROVED);
        assertThat(response.getApprovedBy()).isEqualTo(actorId);
    }

    @Test
    void approve_lanzaExcepcion_siLaTransicionEsInvalida() {
        when(documentRepo.findById(documentId)).thenReturn(Optional.of(document(DocumentStatus.DRAFT)));

        assertThatThrownBy(() -> service.approve(documentId))
                .isInstanceOf(InvalidTransitionException.class);
    }

    // ── reject() ─────────────────────────────────────────────────────────────────

    @Test
    void reject_inReview_pasaARejectedConMotivo() {
        when(documentRepo.findById(documentId)).thenReturn(Optional.of(document(DocumentStatus.IN_REVIEW)));

        RejectDocumentRequest req = new RejectDocumentRequest();
        req.setReason("Falta información legal");

        PrivacyDocumentResponse response = service.reject(documentId, req);

        assertThat(response.getStatus()).isEqualTo(DocumentStatus.REJECTED);
        assertThat(response.getRejectionReason()).isEqualTo("Falta información legal");
    }

    @Test
    void reject_lanzaExcepcion_siLaTransicionEsInvalida() {
        when(documentRepo.findById(documentId)).thenReturn(Optional.of(document(DocumentStatus.DRAFT)));

        assertThatThrownBy(() -> service.reject(documentId, new RejectDocumentRequest()))
                .isInstanceOf(InvalidTransitionException.class);
    }

    // ── publish() ────────────────────────────────────────────────────────────────

    @Test
    void publish_approved_generaPdfHashYQuedaPublished() {
        PrivacyDocuments doc = document(DocumentStatus.APPROVED);
        Purposes purpose = approvedActivePurpose();
        when(documentRepo.findById(documentId)).thenReturn(Optional.of(doc));
        when(purposeRepo.findByDocument_IdAndIsActiveTrue(documentId)).thenReturn(List.of(activeLink(doc, purpose)));
        when(documentRepo.findByTemplateIdAndStatusAndIsActiveTrue(templateId, DocumentStatus.PUBLISHED))
                .thenReturn(Optional.empty());
        when(pdfGenerator.generate(doc)).thenReturn(new PdfGeneratorService.PdfResult(new byte[]{1, 2, 3}, "hash-pdf"));
        when(domainsRepo.findById(domainId)).thenReturn(Optional.of(new Domains()));

        PrivacyDocumentResponse response = service.publish(documentId);

        assertThat(response.getStatus()).isEqualTo(DocumentStatus.PUBLISHED);
        assertThat(response.getHashSha256()).isEqualTo("hash-pdf");
    }

    @Test
    void publish_archivaAutomaticamenteLaVersionPublishedAnteriorDelMismoTemplate() {
        PrivacyDocuments doc = document(DocumentStatus.APPROVED);
        Purposes purpose = approvedActivePurpose();
        PrivacyDocuments previous = document(DocumentStatus.PUBLISHED);
        previous.setId(UUID.randomUUID());

        when(documentRepo.findById(documentId)).thenReturn(Optional.of(doc));
        when(purposeRepo.findByDocument_IdAndIsActiveTrue(documentId)).thenReturn(List.of(activeLink(doc, purpose)));
        when(documentRepo.findByTemplateIdAndStatusAndIsActiveTrue(templateId, DocumentStatus.PUBLISHED))
                .thenReturn(Optional.of(previous));
        when(pdfGenerator.generate(doc)).thenReturn(new PdfGeneratorService.PdfResult(new byte[]{1}, "hash-pdf"));
        when(domainsRepo.findById(domainId)).thenReturn(Optional.of(new Domains()));

        service.publish(documentId);

        assertThat(previous.getStatus()).isEqualTo(DocumentStatus.ARCHIVED);
    }

    @Test
    void publish_notificaAlSolicitanteOriginalDelPurposeRequest() {
        PrivacyDocuments doc = document(DocumentStatus.APPROVED);
        Purposes purpose = approvedActivePurpose();
        UUID purposeRequestId = UUID.randomUUID();
        purpose.setPurposeRequestId(purposeRequestId);

        PurposeRequests pr = new PurposeRequests();
        pr.setId(purposeRequestId);
        pr.setRequesterId("jefe-keycloak-id");

        when(documentRepo.findById(documentId)).thenReturn(Optional.of(doc));
        when(purposeRepo.findByDocument_IdAndIsActiveTrue(documentId)).thenReturn(List.of(activeLink(doc, purpose)));
        when(documentRepo.findByTemplateIdAndStatusAndIsActiveTrue(templateId, DocumentStatus.PUBLISHED))
                .thenReturn(Optional.empty());
        when(pdfGenerator.generate(doc)).thenReturn(new PdfGeneratorService.PdfResult(new byte[]{1}, "hash-pdf"));
        when(domainsRepo.findById(domainId)).thenReturn(Optional.of(new Domains()));
        when(purposeRequestsRepo.findById(purposeRequestId)).thenReturn(Optional.of(pr));

        service.publish(documentId);

        verify(notificationService).create(
                org.mockito.ArgumentMatchers.eq("jefe-keycloak-id"),
                any(), any(), any(), org.mockito.ArgumentMatchers.eq(purposeId));
    }

    @Test
    void publish_lanzaExcepcion_siNoTienePurposesActivas() {
        when(documentRepo.findById(documentId)).thenReturn(Optional.of(document(DocumentStatus.APPROVED)));
        when(purposeRepo.findByDocument_IdAndIsActiveTrue(documentId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.publish(documentId))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void publish_lanzaExcepcion_siAlgunaPurposeVinculadaNoEstaAprobada() {
        PrivacyDocuments doc = document(DocumentStatus.APPROVED);
        Purposes notApproved = approvedActivePurpose();
        notApproved.setApprovedBy(null);
        when(documentRepo.findById(documentId)).thenReturn(Optional.of(doc));
        when(purposeRepo.findByDocument_IdAndIsActiveTrue(documentId)).thenReturn(List.of(activeLink(doc, notApproved)));

        assertThatThrownBy(() -> service.publish(documentId))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void publish_lanzaExcepcion_siLaTransicionEsInvalida() {
        when(documentRepo.findById(documentId)).thenReturn(Optional.of(document(DocumentStatus.DRAFT)));

        assertThatThrownBy(() -> service.publish(documentId))
                .isInstanceOf(InvalidTransitionException.class);
    }

    // ── archive() ────────────────────────────────────────────────────────────────

    @Test
    void archive_published_pasaAArchived() {
        when(documentRepo.findById(documentId)).thenReturn(Optional.of(document(DocumentStatus.PUBLISHED)));
        when(purposeRepo.existsByDocument_IdAndIsActiveTrue(documentId)).thenReturn(false);

        PrivacyDocumentResponse response = service.archive(documentId);

        assertThat(response.getStatus()).isEqualTo(DocumentStatus.ARCHIVED);
    }

    @Test
    void archive_lanzaExcepcion_siTienePurposesActivas() {
        when(documentRepo.findById(documentId)).thenReturn(Optional.of(document(DocumentStatus.PUBLISHED)));
        when(purposeRepo.existsByDocument_IdAndIsActiveTrue(documentId)).thenReturn(true);

        assertThatThrownBy(() -> service.archive(documentId))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void archive_lanzaExcepcion_siLaTransicionEsInvalida() {
        when(documentRepo.findById(documentId)).thenReturn(Optional.of(document(DocumentStatus.DRAFT)));
        when(purposeRepo.existsByDocument_IdAndIsActiveTrue(documentId)).thenReturn(false);

        assertThatThrownBy(() -> service.archive(documentId))
                .isInstanceOf(InvalidTransitionException.class);
    }

    // ── downloadPdf() ────────────────────────────────────────────────────────────

    @Test
    void downloadPdf_devuelveLosBytesDelPdf() {
        PrivacyDocuments doc = document(DocumentStatus.PUBLISHED);
        doc.setPdfContent(new byte[]{1, 2, 3});
        when(documentRepo.findById(documentId)).thenReturn(Optional.of(doc));

        byte[] result = service.downloadPdf(documentId);

        assertThat(result).containsExactly(1, 2, 3);
    }

    @Test
    void downloadPdf_lanzaExcepcion_siNoTienePdfGenerado() {
        when(documentRepo.findById(documentId)).thenReturn(Optional.of(document(DocumentStatus.DRAFT)));

        assertThatThrownBy(() -> service.downloadPdf(documentId))
                .isInstanceOf(BusinessValidationException.class);
    }

    // ── verify() ─────────────────────────────────────────────────────────────────

    @Test
    void verify_hashCoincide_devuelveHashMatchTrue() {
        PrivacyDocuments doc = document(DocumentStatus.PUBLISHED);
        doc.setPdfContent(new byte[]{1, 2, 3});
        doc.setHashSha256("hash-original");
        when(documentRepo.findById(documentId)).thenReturn(Optional.of(doc));
        when(pdfGenerator.verify(doc.getPdfContent(), "hash-original")).thenReturn(true);

        VerifyResponse response = service.verify(documentId);

        assertThat(response.isHashMatch()).isTrue();
    }

    @Test
    void verify_hashNoCoincide_devuelveHashMatchFalseConHashCalculado() {
        PrivacyDocuments doc = document(DocumentStatus.PUBLISHED);
        doc.setPdfContent(new byte[]{1, 2, 3});
        doc.setHashSha256("hash-original");
        when(documentRepo.findById(documentId)).thenReturn(Optional.of(doc));
        when(pdfGenerator.verify(doc.getPdfContent(), "hash-original")).thenReturn(false);
        when(pdfGenerator.computeCurrentHash(doc.getPdfContent())).thenReturn("hash-alterado");

        VerifyResponse response = service.verify(documentId);

        assertThat(response.isHashMatch()).isFalse();
        assertThat(response.getComputedHash()).isEqualTo("hash-alterado");
    }

    @Test
    void verify_sinPdfGenerado_devuelveHashMatchFalseConMensajeExplicativo() {
        when(documentRepo.findById(documentId)).thenReturn(Optional.of(document(DocumentStatus.DRAFT)));

        VerifyResponse response = service.verify(documentId);

        assertThat(response.isHashMatch()).isFalse();
        assertThat(response.getMessage()).contains("no ha sido publicado");
    }

    // ── recalculateHash() ────────────────────────────────────────────────────────

    @Test
    void recalculateHash_sinPdf_devuelveNull() {
        when(documentRepo.findById(documentId)).thenReturn(Optional.of(document(DocumentStatus.DRAFT)));

        assertThat(service.recalculateHash(documentId)).isNull();
    }

    @Test
    void recalculateHash_conPdf_devuelveElHashRecalculado() {
        PrivacyDocuments doc = document(DocumentStatus.PUBLISHED);
        doc.setPdfContent(new byte[]{1, 2, 3});
        when(documentRepo.findById(documentId)).thenReturn(Optional.of(doc));
        when(pdfGenerator.computeCurrentHash(doc.getPdfContent())).thenReturn("hash-recalculado");

        assertThat(service.recalculateHash(documentId)).isEqualTo("hash-recalculado");
    }

    // ── getActive() ──────────────────────────────────────────────────────────────

    @Test
    void getActive_devuelveLaVersionPublicadaDeLaCategoria() {
        when(documentRepo.findTopByCategoryAndStatusAndIsActiveTrueOrderByVersionDesc(
                DocumentCategory.MARKETING_DIRECTO, DocumentStatus.PUBLISHED))
                .thenReturn(Optional.of(document(DocumentStatus.PUBLISHED)));

        PrivacyDocumentResponse response = service.getActive(DocumentCategory.MARKETING_DIRECTO);

        assertThat(response.getStatus()).isEqualTo(DocumentStatus.PUBLISHED);
    }

    @Test
    void getActive_lanzaExcepcion_siNoHayNingunaPublicada() {
        when(documentRepo.findTopByCategoryAndStatusAndIsActiveTrueOrderByVersionDesc(
                DocumentCategory.MARKETING_DIRECTO, DocumentStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getActive(DocumentCategory.MARKETING_DIRECTO))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    // ── newVersion() ─────────────────────────────────────────────────────────────

    @Test
    void newVersion_creaNuevaVersionDraftHeredandoCampos() {
        PrivacyDocuments source = document(DocumentStatus.PUBLISHED);
        when(documentRepo.findById(documentId)).thenReturn(Optional.of(source));
        when(documentRepo.existsByDocumentFamilyIdAndStatusAndIsActiveTrue(documentId, DocumentStatus.DRAFT))
                .thenReturn(false);
        when(documentRepo.findByDocumentFamilyIdAndIsActiveTrueOrderByVersionDesc(documentId))
                .thenReturn(List.of(source));

        PrivacyDocumentResponse response = service.newVersion(documentId);

        assertThat(response.getStatus()).isEqualTo(DocumentStatus.DRAFT);
        assertThat(response.getVersion()).isEqualTo(2);
        assertThat(response.getName()).isEqualTo(source.getName());
    }

    @Test
    void newVersion_lanzaExcepcion_siElOrigenEstaArchivado() {
        when(documentRepo.findById(documentId)).thenReturn(Optional.of(document(DocumentStatus.ARCHIVED)));

        assertThatThrownBy(() -> service.newVersion(documentId))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void newVersion_lanzaExcepcion_siYaExisteUnDraftActivoEnLaFamilia() {
        when(documentRepo.findById(documentId)).thenReturn(Optional.of(document(DocumentStatus.PUBLISHED)));
        when(documentRepo.existsByDocumentFamilyIdAndStatusAndIsActiveTrue(documentId, DocumentStatus.DRAFT))
                .thenReturn(true);

        assertThatThrownBy(() -> service.newVersion(documentId))
                .isInstanceOf(BusinessValidationException.class);
    }

    // ── getByFamily() ────────────────────────────────────────────────────────────

    @Test
    void getByFamily_usuarioPrivilegiado_veTodasLasVersiones() {
        when(documentRepo.findByDocumentFamilyIdAndIsActiveTrueOrderByVersionDesc(documentId))
                .thenReturn(List.of(document(DocumentStatus.DRAFT), document(DocumentStatus.PUBLISHED)));

        List<PrivacyDocumentResponse> result = service.getByFamily(documentId);

        assertThat(result).hasSize(2);
    }

    @Test
    void getByFamily_usuarioPublico_veSoloLasPublicadas() {
        asPublicUser();
        when(documentRepo.findByDocumentFamilyIdAndIsActiveTrueOrderByVersionDesc(documentId))
                .thenReturn(List.of(document(DocumentStatus.DRAFT), document(DocumentStatus.PUBLISHED)));

        List<PrivacyDocumentResponse> result = service.getByFamily(documentId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(DocumentStatus.PUBLISHED);
    }
}
