package com.leydata.backend.audit.application.service;

import com.leydata.backend.agreement.application.service.AgreementService;
import com.leydata.backend.agreement.domain.exception.AgreementNotFoundException;
import com.leydata.backend.agreement.infrastructure.persistence.AgreementsRepository;
import com.leydata.backend.audit.application.dto.EntityIntegrityLogResponse;
import com.leydata.backend.audit.infrastructure.persistence.EntityIntegrityLogRepository;
import com.leydata.backend.entity.Agreements;
import com.leydata.backend.entity.EntityIntegrityLog;
import com.leydata.backend.entity.PrivacyDocuments;
import com.leydata.backend.entity.Purposes;
import com.leydata.backend.entity.Templates;
import com.leydata.backend.privacydoc.application.service.PrivacyDocumentService;
import com.leydata.backend.privacydoc.domain.exception.BusinessValidationException;
import com.leydata.backend.privacydoc.domain.exception.DocumentNotFoundException;
import com.leydata.backend.privacydoc.infrastructure.persistence.PrivacyDocumentsRepository;
import com.leydata.backend.purposes.application.service.PurposeService;
import com.leydata.backend.purposes.domain.exception.PurposeNotFoundException;
import com.leydata.backend.purposes.infrastructure.persistence.PurposesRepository;
import com.leydata.backend.template.application.service.TemplateService;
import com.leydata.backend.template.domain.exception.TemplateNotFoundException;
import com.leydata.backend.template.infrastructure.persistence.TemplatesRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
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

    private final UUID entityId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(integrityLogRepo.save(org.mockito.ArgumentMatchers.any(EntityIntegrityLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ── AGREEMENT ────────────────────────────────────────────────────────────────

    @Test
    void verify_agreementConHashCoincidente_devuelveIsValidTrue() {
        Agreements a = new Agreements();
        a.setId(entityId);
        a.setHashSha256("hash-actual");
        when(agreementsRepo.findById(entityId)).thenReturn(Optional.of(a));
        when(agreementService.recalculateHash(entityId)).thenReturn("hash-actual");
        when(integrityLogRepo.findTopByOrderByCreatedAtDesc()).thenReturn(Optional.empty());

        EntityIntegrityLogResponse response = verifier.verify("AGREEMENT", entityId, "MANUAL", "actor-1");

        assertThat(response.getIsValid()).isTrue();
        assertThat(response.getErrorDetail()).isNull();
    }

    @Test
    void verify_agreementConHashDistinto_devuelveIsValidFalseConErrorDetail() {
        Agreements a = new Agreements();
        a.setId(entityId);
        a.setHashSha256("hash-almacenado");
        when(agreementsRepo.findById(entityId)).thenReturn(Optional.of(a));
        when(agreementService.recalculateHash(entityId)).thenReturn("hash-recalculado-distinto");
        when(integrityLogRepo.findTopByOrderByCreatedAtDesc()).thenReturn(Optional.empty());

        EntityIntegrityLogResponse response = verifier.verify("AGREEMENT", entityId, "MANUAL", "actor-1");

        assertThat(response.getIsValid()).isFalse();
        assertThat(response.getErrorDetail()).isEqualTo("El hash recalculado no coincide con el almacenado");
    }

    @Test
    void verify_agreementInexistente_lanzaAgreementNotFoundException() {
        when(agreementsRepo.findById(entityId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> verifier.verify("AGREEMENT", entityId, "MANUAL", null))
                .isInstanceOf(AgreementNotFoundException.class);
    }

    // ── TEMPLATE ─────────────────────────────────────────────────────────────────

    @Test
    void verify_templateValido_devuelveIsValidTrue() {
        Templates t = new Templates();
        t.setId(entityId);
        t.setHashSha256("hash-actual");
        when(templatesRepo.findById(entityId)).thenReturn(Optional.of(t));
        when(templateService.recalculateHash(entityId)).thenReturn("hash-actual");
        when(integrityLogRepo.findTopByOrderByCreatedAtDesc()).thenReturn(Optional.empty());

        EntityIntegrityLogResponse response = verifier.verify("TEMPLATE", entityId, "MANUAL", null);

        assertThat(response.getIsValid()).isTrue();
    }

    @Test
    void verify_templateInexistente_lanzaTemplateNotFoundException() {
        when(templatesRepo.findById(entityId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> verifier.verify("TEMPLATE", entityId, "MANUAL", null))
                .isInstanceOf(TemplateNotFoundException.class);
    }

    // ── DOCUMENT ─────────────────────────────────────────────────────────────────

    @Test
    void verify_documentValido_devuelveIsValidTrue() {
        PrivacyDocuments d = new PrivacyDocuments();
        d.setId(entityId);
        d.setHashSha256("hash-actual");
        when(privacyDocumentsRepo.findById(entityId)).thenReturn(Optional.of(d));
        when(privacyDocumentService.recalculateHash(entityId)).thenReturn("hash-actual");
        when(integrityLogRepo.findTopByOrderByCreatedAtDesc()).thenReturn(Optional.empty());

        EntityIntegrityLogResponse response = verifier.verify("DOCUMENT", entityId, "MANUAL", null);

        assertThat(response.getIsValid()).isTrue();
    }

    @Test
    void verify_documentInexistente_lanzaDocumentNotFoundException() {
        when(privacyDocumentsRepo.findById(entityId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> verifier.verify("DOCUMENT", entityId, "MANUAL", null))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    // ── PURPOSE ──────────────────────────────────────────────────────────────────

    @Test
    void verify_purposeValido_devuelveIsValidTrue() {
        Purposes p = new Purposes();
        p.setId(entityId);
        p.setHashSha256("hash-actual");
        when(purposesRepo.findById(entityId)).thenReturn(Optional.of(p));
        when(purposeService.recalculateHash(entityId)).thenReturn("hash-actual");
        when(integrityLogRepo.findTopByOrderByCreatedAtDesc()).thenReturn(Optional.empty());

        EntityIntegrityLogResponse response = verifier.verify("PURPOSE", entityId, "MANUAL", null);

        assertThat(response.getIsValid()).isTrue();
    }

    @Test
    void verify_purposeInexistente_lanzaPurposeNotFoundException() {
        when(purposesRepo.findById(entityId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> verifier.verify("PURPOSE", entityId, "MANUAL", null))
                .isInstanceOf(PurposeNotFoundException.class);
    }

    // ── entityType inválido ──────────────────────────────────────────────────────

    @Test
    void verify_entityTypeInvalido_lanzaBusinessValidationException() {
        assertThatThrownBy(() -> verifier.verify("USER", entityId, "MANUAL", null))
                .isInstanceOf(BusinessValidationException.class);
    }

    // ── cadena de hash del log ───────────────────────────────────────────────────

    @Test
    void verify_primeraVerificacion_usaGenesisComoHashPrevio() {
        Templates t = new Templates();
        t.setId(entityId);
        t.setHashSha256("hash-actual");
        when(templatesRepo.findById(entityId)).thenReturn(Optional.of(t));
        when(templateService.recalculateHash(entityId)).thenReturn("hash-actual");
        when(integrityLogRepo.findTopByOrderByCreatedAtDesc()).thenReturn(Optional.empty());

        verifier.verify("TEMPLATE", entityId, "MANUAL", null);

        ArgumentCaptor<EntityIntegrityLog> captor = ArgumentCaptor.forClass(EntityIntegrityLog.class);
        verify(integrityLogRepo).save(captor.capture());
        assertThat(captor.getValue().getPreviousHashSha256Id()).isEqualTo("GENESIS");
    }

    @Test
    void verify_noEsPrimeraVerificacion_encadenaConHashDelUltimoLogRegistrado() {
        Templates t = new Templates();
        t.setId(entityId);
        t.setHashSha256("hash-actual");
        when(templatesRepo.findById(entityId)).thenReturn(Optional.of(t));
        when(templateService.recalculateHash(entityId)).thenReturn("hash-actual");

        EntityIntegrityLog previousLog = new EntityIntegrityLog();
        previousLog.setHashSha256("hash-del-log-anterior");
        when(integrityLogRepo.findTopByOrderByCreatedAtDesc()).thenReturn(Optional.of(previousLog));

        verifier.verify("TEMPLATE", entityId, "MANUAL", null);

        ArgumentCaptor<EntityIntegrityLog> captor = ArgumentCaptor.forClass(EntityIntegrityLog.class);
        verify(integrityLogRepo).save(captor.capture());
        assertThat(captor.getValue().getPreviousHashSha256Id()).isEqualTo("hash-del-log-anterior");
    }
}
