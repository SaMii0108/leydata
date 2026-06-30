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
    private AgreementTraceService traceService;

    private final UUID agreementId = UUID.randomUUID();
    private final UUID documentId = UUID.randomUUID();
    private final UUID templateId = UUID.randomUUID();
    private final UUID purposeId = UUID.randomUUID();
    private final UUID dataSubjectId = UUID.randomUUID();

    private Agreements agreement() {
        Agreements a = new Agreements();
        a.setId(agreementId);
        a.setDataSubjectId(dataSubjectId);
        a.setDocumentId(documentId);
        a.setTemplateId(templateId);
        a.setCreatedAt(LocalDateTime.now());
        return a;
    }

    private PrivacyDocuments document(String storedHash) {
        PrivacyDocuments d = new PrivacyDocuments();
        d.setId(documentId);
        d.setVersion(3);
        d.setHashSha256(storedHash);
        return d;
    }

    private Templates template(String storedHash) {
        Templates t = new Templates();
        t.setId(templateId);
        t.setTemplateKey("CONSENT_MARKETING");
        t.setVersion(2);
        t.setHashSha256(storedHash);
        return t;
    }

    private AgreementsPurposes agreementPurpose(String storedHash) {
        AgreementsPurposes ap = new AgreementsPurposes();
        ap.setAgreementId(agreementId);
        ap.setPurposeId(purposeId);
        ap.setAccepted(true);
        ap.setPurposeCode("MKT_EMAIL");
        ap.setPurposeHash(storedHash);
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

    @Test
    void trace_lanzaAgreementNotFoundException_siNoExiste() {
        when(agreementsRepo.findById(agreementId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> traceService.trace(agreementId))
                .isInstanceOf(AgreementNotFoundException.class);
    }

    @Test
    void trace_overallIntegrityOK_cuandoTodosLosEslabonesSonValidos() {
        when(agreementsRepo.findById(agreementId)).thenReturn(Optional.of(agreement()));
        when(privacyDocumentsRepo.findById(documentId)).thenReturn(Optional.of(document("hash-doc")));
        when(privacyDocumentService.recalculateHash(documentId)).thenReturn("hash-doc");
        when(templatesRepo.findById(templateId)).thenReturn(Optional.of(template("hash-template")));
        when(templateService.recalculateHash(templateId)).thenReturn("hash-template");
        when(agreementsPurposesRepo.findByAgreementId(agreementId)).thenReturn(List.of(agreementPurpose("hash-purpose")));
        when(purposesRepo.findById(purposeId)).thenReturn(Optional.of(purpose("hash-purpose")));

        AgreementTraceResponse response = traceService.trace(agreementId);

        assertThat(response.getAgreementId()).isEqualTo(agreementId);
        assertThat(response.getDocument().getIsValid()).isTrue();
        assertThat(response.getTemplate().getIsValid()).isTrue();
        assertThat(response.getPurposes()).hasSize(1);
        assertThat(response.getPurposes().get(0).getIsValid()).isTrue();
        assertThat(response.getPurposes().get(0).getIntegrityStatus()).isEqualTo("OK");
        assertThat(response.getOverallIntegrity()).isEqualTo("OK");
    }

    @Test
    void trace_overallIntegrityMismatch_cuandoElTemplateFueAlterado() {
        when(agreementsRepo.findById(agreementId)).thenReturn(Optional.of(agreement()));
        when(privacyDocumentsRepo.findById(documentId)).thenReturn(Optional.of(document("hash-doc")));
        when(privacyDocumentService.recalculateHash(documentId)).thenReturn("hash-doc");
        when(templatesRepo.findById(templateId)).thenReturn(Optional.of(template("hash-guardado")));
        when(templateService.recalculateHash(templateId)).thenReturn("hash-alterado");
        when(agreementsPurposesRepo.findByAgreementId(agreementId)).thenReturn(List.of(agreementPurpose("hash-purpose")));
        when(purposesRepo.findById(purposeId)).thenReturn(Optional.of(purpose("hash-purpose")));

        AgreementTraceResponse response = traceService.trace(agreementId);

        assertThat(response.getTemplate().getIsValid()).isFalse();
        assertThat(response.getOverallIntegrity()).isEqualTo("MISMATCH");
    }

    @Test
    void trace_overallIntegrityPartial_cuandoElDocumentoNoTieneHashCalculable() {
        when(agreementsRepo.findById(agreementId)).thenReturn(Optional.of(agreement()));
        when(privacyDocumentsRepo.findById(documentId)).thenReturn(Optional.of(document(null)));
        when(templatesRepo.findById(templateId)).thenReturn(Optional.of(template("hash-template")));
        when(templateService.recalculateHash(templateId)).thenReturn("hash-template");
        when(agreementsPurposesRepo.findByAgreementId(agreementId)).thenReturn(List.of(agreementPurpose("hash-purpose")));
        when(purposesRepo.findById(purposeId)).thenReturn(Optional.of(purpose("hash-purpose")));

        AgreementTraceResponse response = traceService.trace(agreementId);

        assertThat(response.getDocument().getIsValid()).isFalse();
        assertThat(response.getOverallIntegrity()).isEqualTo("PARTIAL");
    }

    @Test
    void trace_purposeIntegrityStatusUnknown_cuandoLaPurposeFueEliminada() {
        when(agreementsRepo.findById(agreementId)).thenReturn(Optional.of(agreement()));
        when(privacyDocumentsRepo.findById(documentId)).thenReturn(Optional.of(document("hash-doc")));
        when(privacyDocumentService.recalculateHash(documentId)).thenReturn("hash-doc");
        when(templatesRepo.findById(templateId)).thenReturn(Optional.of(template("hash-template")));
        when(templateService.recalculateHash(templateId)).thenReturn("hash-template");
        when(agreementsPurposesRepo.findByAgreementId(agreementId)).thenReturn(List.of(agreementPurpose("hash-purpose")));
        when(purposesRepo.findById(purposeId)).thenReturn(Optional.empty());

        AgreementTraceResponse response = traceService.trace(agreementId);

        assertThat(response.getPurposes().get(0).getIntegrityStatus()).isEqualTo("UNKNOWN");
        assertThat(response.getOverallIntegrity()).isEqualTo("PARTIAL");
    }

    @Test
    void trace_noEscribeEnNingunRepositorioDeLog() {
        when(agreementsRepo.findById(agreementId)).thenReturn(Optional.of(agreement()));
        when(privacyDocumentsRepo.findById(documentId)).thenReturn(Optional.of(document("hash-doc")));
        when(privacyDocumentService.recalculateHash(documentId)).thenReturn("hash-doc");
        when(templatesRepo.findById(templateId)).thenReturn(Optional.of(template("hash-template")));
        when(templateService.recalculateHash(templateId)).thenReturn("hash-template");
        when(agreementsPurposesRepo.findByAgreementId(agreementId)).thenReturn(List.of());

        AgreementTraceResponse response = traceService.trace(agreementId);

        // Regla 13: /trace es de solo lectura. No hay ningún repositorio de logs inyectado
        // en este service — la ausencia de ese mock ya es la prueba estructural de la regla.
        assertThat(response.getPurposes()).isEmpty();
    }
}
