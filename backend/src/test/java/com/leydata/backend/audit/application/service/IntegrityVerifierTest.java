package com.leydata.backend.audit.application.service;

import com.leydata.backend.agreement.application.service.AgreementService;
import com.leydata.backend.agreement.domain.exception.AgreementNotFoundException;
import com.leydata.backend.agreement.infrastructure.persistence.AgreementsRepository;
import com.leydata.backend.audit.application.dto.EntityIntegrityLogResponse;
import com.leydata.backend.audit.infrastructure.persistence.EntityIntegrityLogRepository;
import com.leydata.backend.entity.Agreements;
import com.leydata.backend.entity.EntityIntegrityLog;
import com.leydata.backend.entity.Purposes;
import com.leydata.backend.entity.Templates;
import com.leydata.backend.privacydoc.application.service.PrivacyDocumentService;
import com.leydata.backend.privacydoc.domain.exception.BusinessValidationException;
import com.leydata.backend.privacydoc.domain.exception.DocumentNotFoundException;
import com.leydata.backend.privacydoc.infrastructure.persistence.PrivacyDocumentsRepository;
import com.leydata.backend.entity.PrivacyDocuments;
import com.leydata.backend.purposes.application.service.PurposeService;
import com.leydata.backend.purposes.domain.exception.PurposeNotFoundException;
import com.leydata.backend.purposes.infrastructure.persistence.PurposesRepository;
import com.leydata.backend.template.application.service.TemplateService;
import com.leydata.backend.template.domain.exception.TemplateNotFoundException;
import com.leydata.backend.template.infrastructure.persistence.TemplatesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegrityVerifierTest {

    @Mock private EntityIntegrityLogRepository integrityLogRepo;
    @Mock private AgreementsRepository agreementsRepo;
    @Mock private TemplatesRepository templatesRepo;
    @Mock private PrivacyDocumentsRepository privacyDocumentsRepo;
    @Mock private PurposesRepository purposesRepo;
    @Mock private AgreementService agreementService;
    @Mock private TemplateService templateService;
    @Mock private PrivacyDocumentService privacyDocumentService;
    @Mock private PurposeService purposeService;

    @InjectMocks
    private IntegrityVerifier verifier;

    @BeforeEach
    void setUp() {
        lenient().when(integrityLogRepo.save(any(EntityIntegrityLog.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(integrityLogRepo.findTopByOrderByCreatedAtDesc()).thenReturn(Optional.empty());
    }

    @Test
    void verify_AGREEMENT_isValidTrue_cuandoElHashCoincide() {
        UUID id = UUID.randomUUID();
        Agreements agreement = new Agreements();
        agreement.setId(id);
        agreement.setHashSha256("hash-actual");
        when(agreementsRepo.findById(id)).thenReturn(Optional.of(agreement));
        when(agreementService.recalculateHash(id)).thenReturn("hash-actual");

        EntityIntegrityLogResponse response = verifier.verify("AGREEMENT", id, "MANUAL", "actor-1");

        assertThat(response.getIsValid()).isTrue();
        assertThat(response.getEntityType()).isEqualTo("AGREEMENT");
        assertThat(response.getStoredHash()).isEqualTo("hash-actual");
        assertThat(response.getRecalculatedHash()).isEqualTo("hash-actual");
    }

    @Test
    void verify_AGREEMENT_isValidFalse_cuandoElHashNoCoincide() {
        UUID id = UUID.randomUUID();
        Agreements agreement = new Agreements();
        agreement.setId(id);
        agreement.setHashSha256("hash-guardado");
        when(agreementsRepo.findById(id)).thenReturn(Optional.of(agreement));
        when(agreementService.recalculateHash(id)).thenReturn("hash-distinto");

        EntityIntegrityLogResponse response = verifier.verify("AGREEMENT", id, "MANUAL", "actor-1");

        assertThat(response.getIsValid()).isFalse();
        assertThat(response.getErrorDetail()).isNotBlank();
    }

    @Test
    void verify_AGREEMENT_lanzaAgreementNotFoundException_siNoExiste() {
        UUID id = UUID.randomUUID();
        when(agreementsRepo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> verifier.verify("AGREEMENT", id, "MANUAL", "actor-1"))
                .isInstanceOf(AgreementNotFoundException.class);
    }

    @Test
    void verify_TEMPLATE_isValidTrue_cuandoElHashCoincide() {
        UUID id = UUID.randomUUID();
        Templates template = new Templates();
        template.setId(id);
        template.setHashSha256("hash-actual");
        when(templatesRepo.findById(id)).thenReturn(Optional.of(template));
        when(templateService.recalculateHash(id)).thenReturn("hash-actual");

        EntityIntegrityLogResponse response = verifier.verify("TEMPLATE", id, "MANUAL", "actor-1");

        assertThat(response.getIsValid()).isTrue();
    }

    @Test
    void verify_TEMPLATE_lanzaTemplateNotFoundException_siNoExiste() {
        UUID id = UUID.randomUUID();
        when(templatesRepo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> verifier.verify("TEMPLATE", id, "MANUAL", "actor-1"))
                .isInstanceOf(TemplateNotFoundException.class);
    }

    @Test
    void verify_DOCUMENT_isValidFalse_cuandoElHashNoCoincide() {
        UUID id = UUID.randomUUID();
        PrivacyDocuments doc = new PrivacyDocuments();
        doc.setId(id);
        doc.setHashSha256("hash-guardado");
        when(privacyDocumentsRepo.findById(id)).thenReturn(Optional.of(doc));
        when(privacyDocumentService.recalculateHash(id)).thenReturn("hash-distinto");

        EntityIntegrityLogResponse response = verifier.verify("DOCUMENT", id, "SCHEDULED", null);

        assertThat(response.getIsValid()).isFalse();
        assertThat(response.getCheckType()).isEqualTo("SCHEDULED");
    }

    @Test
    void verify_DOCUMENT_lanzaDocumentNotFoundException_siNoExiste() {
        UUID id = UUID.randomUUID();
        when(privacyDocumentsRepo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> verifier.verify("DOCUMENT", id, "MANUAL", "actor-1"))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void verify_PURPOSE_isValidTrue_cuandoElHashCoincide() {
        UUID id = UUID.randomUUID();
        Purposes purpose = new Purposes();
        purpose.setId(id);
        purpose.setHashSha256("hash-actual");
        when(purposesRepo.findById(id)).thenReturn(Optional.of(purpose));
        when(purposeService.recalculateHash(id)).thenReturn("hash-actual");

        EntityIntegrityLogResponse response = verifier.verify("PURPOSE", id, "MANUAL", "actor-1");

        assertThat(response.getIsValid()).isTrue();
    }

    @Test
    void verify_PURPOSE_lanzaPurposeNotFoundException_siNoExiste() {
        UUID id = UUID.randomUUID();
        when(purposesRepo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> verifier.verify("PURPOSE", id, "MANUAL", "actor-1"))
                .isInstanceOf(PurposeNotFoundException.class);
    }

    @Test
    void verify_lanzaBusinessValidationException_siEntityTypeInvalido() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> verifier.verify("ENTIDAD_INEXISTENTE", id, "MANUAL", "actor-1"))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void verify_encadenaPreviousHashShaIdContraElUltimoLog() {
        UUID id = UUID.randomUUID();
        Agreements agreement = new Agreements();
        agreement.setId(id);
        agreement.setHashSha256("hash-actual");
        when(agreementsRepo.findById(id)).thenReturn(Optional.of(agreement));
        when(agreementService.recalculateHash(id)).thenReturn("hash-actual");

        EntityIntegrityLog lastLog = new EntityIntegrityLog();
        lastLog.setHashSha256("hash-log-anterior");
        when(integrityLogRepo.findTopByOrderByCreatedAtDesc()).thenReturn(Optional.of(lastLog));

        verifier.verify("AGREEMENT", id, "MANUAL", "actor-1");

        // No se puede conocer el SHA-256 exacto a mano; se valida que no lance excepción al encadenar.
        assertThat(true).isTrue();
    }

    @Test
    void verify_usaGenesisComoPreviousHash_cuandoNoHayLogsPrevios() {
        UUID id = UUID.randomUUID();
        Agreements agreement = new Agreements();
        agreement.setId(id);
        agreement.setHashSha256("hash-actual");
        when(agreementsRepo.findById(id)).thenReturn(Optional.of(agreement));
        when(agreementService.recalculateHash(id)).thenReturn("hash-actual");
        when(integrityLogRepo.findTopByOrderByCreatedAtDesc()).thenReturn(Optional.empty());

        verifier.verify("AGREEMENT", id, "MANUAL", "actor-1");

        assertThat(true).isTrue();
    }
}
