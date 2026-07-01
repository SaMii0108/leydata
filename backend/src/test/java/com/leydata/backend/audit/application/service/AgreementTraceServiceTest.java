package com.leydata.backend.audit.application.service;

import com.leydata.backend.agreement.domain.exception.AgreementNotFoundException;
import com.leydata.backend.agreement.infrastructure.persistence.AgreementsPurposesRepository;
import com.leydata.backend.agreement.infrastructure.persistence.AgreementsRepository;
import com.leydata.backend.audit.application.dto.AgreementTraceResponse;
import com.leydata.backend.entity.Agreements;
import com.leydata.backend.entity.AgreementsPurposes;
import com.leydata.backend.entity.PrivacyDocuments;
import com.leydata.backend.entity.Purposes;
import com.leydata.backend.entity.Templates;
import com.leydata.backend.privacydoc.application.service.PrivacyDocumentService;
import com.leydata.backend.privacydoc.infrastructure.persistence.PrivacyDocumentsRepository;
import com.leydata.backend.purposes.infrastructure.persistence.PurposesRepository;
import com.leydata.backend.template.application.service.TemplateService;
import com.leydata.backend.template.infrastructure.persistence.TemplatesRepository;

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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgreementTraceServiceTest {

    @Mock private AgreementsRepository agreementsRepo;
    @Mock private AgreementsPurposesRepository agreementsPurposesRepo;
    @Mock private PrivacyDocumentsRepository privacyDocumentsRepo;
    @Mock private TemplatesRepository templatesRepo;
    @Mock private PurposesRepository purposesRepo;
    @Mock private PrivacyDocumentService privacyDocumentService;
    @Mock private TemplateService templateService;

    @InjectMocks
    private AgreementTraceService service;

    private final UUID agreementId = UUID.randomUUID();
    private final UUID documentId = UUID.randomUUID();
    private final UUID templateId = UUID.randomUUID();
    private final UUID purposeId = UUID.randomUUID();

    private Agreements agreement() {
        Agreements a = new Agreements();
        a.setId(agreementId);
        a.setDataSubjectId(UUID.randomUUID());
        a.setDocumentId(documentId);
        a.setTemplateId(templateId);
        a.setCreatedAt(LocalDateTime.now());
        return a;
    }

    private PrivacyDocuments document(String hash) {
        PrivacyDocuments d = new PrivacyDocuments();
        d.setId(documentId);
        d.setVersion(1);
        d.setHashSha256(hash);
        return d;
    }

    private Templates template(String hash) {
        Templates t = new Templates();
        t.setId(templateId);
        t.setTemplateKey("CONSENT_X");
        t.setVersion(1);
        t.setHashSha256(hash);
        return t;
    }

    private AgreementsPurposes agreementPurpose(String snapshotHash) {
        AgreementsPurposes ap = new AgreementsPurposes();
        ap.setAgreementId(agreementId);
        ap.setPurposeId(purposeId);
        ap.setPurposeCode("MARKETING");
        ap.setAccepted(true);
        ap.setPurposeHash(snapshotHash);
        return ap;
    }

    private Purposes purpose(String currentHash) {
        Purposes p = new Purposes();
        p.setId(purposeId);
        p.setPurposeFamilyId(purposeId);
        p.setVersion(1);
        p.setHashSha256(currentHash);
        return p;
    }

    private void mockValidChain() {
        when(agreementsRepo.findById(agreementId)).thenReturn(Optional.of(agreement()));
        when(privacyDocumentsRepo.findById(documentId)).thenReturn(Optional.of(document("doc-hash")));
        when(privacyDocumentService.recalculateHash(documentId)).thenReturn("doc-hash");
        when(templatesRepo.findById(templateId)).thenReturn(Optional.of(template("tpl-hash")));
        when(templateService.recalculateHash(templateId)).thenReturn("tpl-hash");
        when(agreementsPurposesRepo.findByAgreementId(agreementId)).thenReturn(List.of(agreementPurpose("purpose-hash")));
        when(purposesRepo.findById(purposeId)).thenReturn(Optional.of(purpose("purpose-hash")));
    }

    @Test
    void trace_todaLaCadenaValida_devuelveOverallIntegrityOk() {
        mockValidChain();

        AgreementTraceResponse response = service.trace(agreementId);

        assertThat(response.getOverallIntegrity()).isEqualTo("OK");
        assertThat(response.getDocument().getIsValid()).isTrue();
        assertThat(response.getTemplate().getIsValid()).isTrue();
        assertThat(response.getPurposes()).hasSize(1);
        assertThat(response.getPurposes().get(0).getIsValid()).isTrue();
        assertThat(response.getPurposes().get(0).getIntegrityStatus()).isEqualTo("OK");
    }

    @Test
    void trace_agreementInexistente_lanzaAgreementNotFoundException() {
        when(agreementsRepo.findById(agreementId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.trace(agreementId))
                .isInstanceOf(AgreementNotFoundException.class);
    }

    @Test
    void trace_documentoConHashAlterado_devuelveOverallIntegrityMismatch() {
        when(agreementsRepo.findById(agreementId)).thenReturn(Optional.of(agreement()));
        when(privacyDocumentsRepo.findById(documentId)).thenReturn(Optional.of(document("doc-hash-original")));
        when(privacyDocumentService.recalculateHash(documentId)).thenReturn("doc-hash-alterado");
        when(templatesRepo.findById(templateId)).thenReturn(Optional.of(template("tpl-hash")));
        when(templateService.recalculateHash(templateId)).thenReturn("tpl-hash");
        when(agreementsPurposesRepo.findByAgreementId(agreementId)).thenReturn(List.of());

        AgreementTraceResponse response = service.trace(agreementId);

        assertThat(response.getDocument().getIsValid()).isFalse();
        assertThat(response.getOverallIntegrity()).isEqualTo("MISMATCH");
    }

    @Test
    void trace_templateConHashAlterado_devuelveOverallIntegrityMismatch() {
        when(agreementsRepo.findById(agreementId)).thenReturn(Optional.of(agreement()));
        when(privacyDocumentsRepo.findById(documentId)).thenReturn(Optional.of(document("doc-hash")));
        when(privacyDocumentService.recalculateHash(documentId)).thenReturn("doc-hash");
        when(templatesRepo.findById(templateId)).thenReturn(Optional.of(template("tpl-hash-original")));
        when(templateService.recalculateHash(templateId)).thenReturn("tpl-hash-alterado");
        when(agreementsPurposesRepo.findByAgreementId(agreementId)).thenReturn(List.of());

        AgreementTraceResponse response = service.trace(agreementId);

        assertThat(response.getTemplate().getIsValid()).isFalse();
        assertThat(response.getOverallIntegrity()).isEqualTo("MISMATCH");
    }

    @Test
    void trace_purposeConDriftPosteriorAlConsentimiento_devuelveIntegrityMismatch() {
        when(agreementsRepo.findById(agreementId)).thenReturn(Optional.of(agreement()));
        when(privacyDocumentsRepo.findById(documentId)).thenReturn(Optional.of(document("doc-hash")));
        when(privacyDocumentService.recalculateHash(documentId)).thenReturn("doc-hash");
        when(templatesRepo.findById(templateId)).thenReturn(Optional.of(template("tpl-hash")));
        when(templateService.recalculateHash(templateId)).thenReturn("tpl-hash");
        when(agreementsPurposesRepo.findByAgreementId(agreementId))
                .thenReturn(List.of(agreementPurpose("purpose-hash-al-consentir")));
        when(purposesRepo.findById(purposeId)).thenReturn(Optional.of(purpose("purpose-hash-actual-modificado")));

        AgreementTraceResponse response = service.trace(agreementId);

        assertThat(response.getPurposes().get(0).getIsValid()).isFalse();
        assertThat(response.getPurposes().get(0).getIntegrityStatus()).isEqualTo("INTEGRITY_MISMATCH");
        assertThat(response.getOverallIntegrity()).isEqualTo("MISMATCH");
    }

    @Test
    void trace_documentoInexistente_devuelvePartialConEslabonUnknown() {
        when(agreementsRepo.findById(agreementId)).thenReturn(Optional.of(agreement()));
        when(privacyDocumentsRepo.findById(documentId)).thenReturn(Optional.empty());
        when(templatesRepo.findById(templateId)).thenReturn(Optional.of(template("tpl-hash")));
        when(templateService.recalculateHash(templateId)).thenReturn("tpl-hash");
        when(agreementsPurposesRepo.findByAgreementId(agreementId)).thenReturn(List.of());

        AgreementTraceResponse response = service.trace(agreementId);

        assertThat(response.getDocument().getIsValid()).isFalse();
        assertThat(response.getOverallIntegrity()).isEqualTo("PARTIAL");
    }

    @Test
    void trace_purposeInexistente_devuelveIntegrityStatusUnknown() {
        when(agreementsRepo.findById(agreementId)).thenReturn(Optional.of(agreement()));
        when(privacyDocumentsRepo.findById(documentId)).thenReturn(Optional.of(document("doc-hash")));
        when(privacyDocumentService.recalculateHash(documentId)).thenReturn("doc-hash");
        when(templatesRepo.findById(templateId)).thenReturn(Optional.of(template("tpl-hash")));
        when(templateService.recalculateHash(templateId)).thenReturn("tpl-hash");
        when(agreementsPurposesRepo.findByAgreementId(agreementId))
                .thenReturn(List.of(agreementPurpose("purpose-hash")));
        when(purposesRepo.findById(purposeId)).thenReturn(Optional.empty());

        AgreementTraceResponse response = service.trace(agreementId);

        assertThat(response.getPurposes().get(0).getIsValid()).isFalse();
        assertThat(response.getPurposes().get(0).getIntegrityStatus()).isEqualTo("UNKNOWN");
        assertThat(response.getOverallIntegrity()).isEqualTo("PARTIAL");
    }

    @Test
    void trace_purposeHashNuloEnElSnapshot_seConsideraUnknown() {
        when(agreementsRepo.findById(agreementId)).thenReturn(Optional.of(agreement()));
        when(privacyDocumentsRepo.findById(documentId)).thenReturn(Optional.of(document("doc-hash")));
        when(privacyDocumentService.recalculateHash(documentId)).thenReturn("doc-hash");
        when(templatesRepo.findById(templateId)).thenReturn(Optional.of(template("tpl-hash")));
        when(templateService.recalculateHash(templateId)).thenReturn("tpl-hash");
        when(agreementsPurposesRepo.findByAgreementId(agreementId))
                .thenReturn(List.of(agreementPurpose(null)));
        when(purposesRepo.findById(purposeId)).thenReturn(Optional.of(purpose("cualquier-hash")));

        AgreementTraceResponse response = service.trace(agreementId);

        assertThat(response.getPurposes().get(0).getIntegrityStatus()).isEqualTo("UNKNOWN");
        assertThat(response.getOverallIntegrity()).isEqualTo("PARTIAL");
    }

    @Test
    void trace_mismatchTienePrioridadSobreUnknown_enElOverallIntegrity() {
        // Documento inexistente (UNKNOWN) + template con hash alterado (MISMATCH) en la misma traza
        when(agreementsRepo.findById(agreementId)).thenReturn(Optional.of(agreement()));
        when(privacyDocumentsRepo.findById(documentId)).thenReturn(Optional.empty());
        when(templatesRepo.findById(templateId)).thenReturn(Optional.of(template("tpl-hash-original")));
        when(templateService.recalculateHash(templateId)).thenReturn("tpl-hash-alterado");
        when(agreementsPurposesRepo.findByAgreementId(agreementId)).thenReturn(List.of());

        AgreementTraceResponse response = service.trace(agreementId);

        assertThat(response.getOverallIntegrity()).isEqualTo("MISMATCH");
    }

    @Test
    void trace_variasPurposes_incluyeTodasEnLaRespuesta() {
        UUID purposeId2 = UUID.randomUUID();
        AgreementsPurposes ap2 = new AgreementsPurposes();
        ap2.setAgreementId(agreementId);
        ap2.setPurposeId(purposeId2);
        ap2.setPurposeCode("ANALYTICS");
        ap2.setAccepted(false);
        ap2.setPurposeHash("hash-2");
        Purposes purpose2 = new Purposes();
        purpose2.setId(purposeId2);
        purpose2.setPurposeFamilyId(purposeId2);
        purpose2.setVersion(1);
        purpose2.setHashSha256("hash-2");

        when(agreementsRepo.findById(agreementId)).thenReturn(Optional.of(agreement()));
        when(privacyDocumentsRepo.findById(documentId)).thenReturn(Optional.of(document("doc-hash")));
        when(privacyDocumentService.recalculateHash(documentId)).thenReturn("doc-hash");
        when(templatesRepo.findById(templateId)).thenReturn(Optional.of(template("tpl-hash")));
        when(templateService.recalculateHash(templateId)).thenReturn("tpl-hash");
        when(agreementsPurposesRepo.findByAgreementId(agreementId))
                .thenReturn(List.of(agreementPurpose("purpose-hash"), ap2));
        when(purposesRepo.findById(purposeId)).thenReturn(Optional.of(purpose("purpose-hash")));
        when(purposesRepo.findById(purposeId2)).thenReturn(Optional.of(purpose2));

        AgreementTraceResponse response = service.trace(agreementId);

        assertThat(response.getPurposes()).hasSize(2);
        assertThat(response.getOverallIntegrity()).isEqualTo("OK");
    }
}
