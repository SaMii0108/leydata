package com.leydata.backend.audit.application.service;

import com.leydata.backend.agreement.infrastructure.persistence.AgreementsRepository;
import com.leydata.backend.entity.Agreements;
import com.leydata.backend.entity.PrivacyDocuments;
import com.leydata.backend.entity.Purposes;
import com.leydata.backend.entity.Templates;
import com.leydata.backend.privacydoc.infrastructure.persistence.PrivacyDocumentsRepository;
import com.leydata.backend.purposes.infrastructure.persistence.PurposesRepository;
import com.leydata.backend.template.infrastructure.persistence.TemplatesRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegritySchedulerTest {

    @Mock private IntegrityVerifier integrityVerifier;
    @Mock private AgreementsRepository agreementsRepo;
    @Mock private TemplatesRepository templatesRepo;
    @Mock private PrivacyDocumentsRepository privacyDocumentsRepo;
    @Mock private PurposesRepository purposesRepo;

    @InjectMocks
    private IntegrityScheduler scheduler;

    private Agreements agreement(UUID id) {
        Agreements a = new Agreements();
        a.setId(id);
        return a;
    }

    private Templates template(UUID id) {
        Templates t = new Templates();
        t.setId(id);
        return t;
    }

    private PrivacyDocuments document(UUID id) {
        PrivacyDocuments d = new PrivacyDocuments();
        d.setId(id);
        return d;
    }

    private Purposes purpose(UUID id) {
        Purposes p = new Purposes();
        p.setId(id);
        return p;
    }

    @Test
    void verifyAllEntities_verificaCadaAgreementTemplateDocumentoYPurpose() {
        UUID agreementId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID purposeId = UUID.randomUUID();

        when(agreementsRepo.findAll()).thenReturn(List.of(agreement(agreementId)));
        when(templatesRepo.findAll()).thenReturn(List.of(template(templateId)));
        when(privacyDocumentsRepo.findAll()).thenReturn(List.of(document(documentId)));
        when(purposesRepo.findAll()).thenReturn(List.of(purpose(purposeId)));

        scheduler.verifyAllEntities();

        verify(integrityVerifier).verify(eq("AGREEMENT"), eq(agreementId), eq("SCHEDULED"), eq(null));
        verify(integrityVerifier).verify(eq("TEMPLATE"), eq(templateId), eq("SCHEDULED"), eq(null));
        verify(integrityVerifier).verify(eq("DOCUMENT"), eq(documentId), eq("SCHEDULED"), eq(null));
        verify(integrityVerifier).verify(eq("PURPOSE"), eq(purposeId), eq("SCHEDULED"), eq(null));
    }

    @Test
    void verifyAllEntities_siUnaVerificacionFalla_continuaConLasSiguientes() {
        UUID agreementId1 = UUID.randomUUID();
        UUID agreementId2 = UUID.randomUUID();

        when(agreementsRepo.findAll()).thenReturn(List.of(agreement(agreementId1), agreement(agreementId2)));
        when(templatesRepo.findAll()).thenReturn(List.of());
        when(privacyDocumentsRepo.findAll()).thenReturn(List.of());
        when(purposesRepo.findAll()).thenReturn(List.of());

        when(integrityVerifier.verify(eq("AGREEMENT"), eq(agreementId1), any(), any()))
                .thenThrow(new RuntimeException("fallo simulado"));

        scheduler.verifyAllEntities();

        verify(integrityVerifier).verify(eq("AGREEMENT"), eq(agreementId1), eq("SCHEDULED"), eq(null));
        verify(integrityVerifier).verify(eq("AGREEMENT"), eq(agreementId2), eq("SCHEDULED"), eq(null));
    }

    @Test
    void verifyAllEntities_sinEntidades_noLlamaAVerify() {
        when(agreementsRepo.findAll()).thenReturn(List.of());
        when(templatesRepo.findAll()).thenReturn(List.of());
        when(privacyDocumentsRepo.findAll()).thenReturn(List.of());
        when(purposesRepo.findAll()).thenReturn(List.of());

        scheduler.verifyAllEntities();

        verify(integrityVerifier, org.mockito.Mockito.never()).verify(any(), any(), any(), any());
    }
}
