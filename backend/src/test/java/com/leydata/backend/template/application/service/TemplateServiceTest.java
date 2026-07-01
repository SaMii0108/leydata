package com.leydata.backend.template.application.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.leydata.backend.audit.application.service.AuditService;
import com.leydata.backend.entity.Domains;
import com.leydata.backend.entity.Purposes;
import com.leydata.backend.entity.TemplatePurposes;
import com.leydata.backend.entity.Templates;
import com.leydata.backend.orgdomain.infrastructure.persistence.DomainsRepository;
import com.leydata.backend.privacydoc.domain.exception.BusinessValidationException;
import com.leydata.backend.purposes.infrastructure.persistence.PurposesRepository;
import com.leydata.backend.shared.SecurityContextHelper;
import com.leydata.backend.template.application.dto.AddTemplatePurposeRequest;
import com.leydata.backend.template.application.dto.CreateTemplateRequest;
import com.leydata.backend.template.application.dto.TemplateResponse;
import com.leydata.backend.template.application.dto.UpdateTemplatePurposeRequest;
import com.leydata.backend.template.domain.exception.TemplateNotFoundException;
import com.leydata.backend.template.infrastructure.persistence.TemplatePurposesRepository;
import com.leydata.backend.template.infrastructure.persistence.TemplatesRepository;

@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {

    @Mock private TemplatesRepository templatesRepo;
    @Mock private TemplatePurposesRepository templatePurposesRepo;
    @Mock private PurposesRepository purposesRepo;
    @Mock private DomainsRepository domainsRepo;
    @Mock private SecurityContextHelper securityContextHelper;
    @Mock private AuditService auditService;

    @InjectMocks
    private TemplateService service;

    // Keycloak ID como String — arquitectura Keycloak-first
    private final String actorKeycloakId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        lenient().doNothing().when(securityContextHelper).requireDpoOrAdmin();
        lenient().when(securityContextHelper.getKeycloakId()).thenReturn(actorKeycloakId);
        lenient().when(securityContextHelper.getActorRole()).thenReturn("DPO");
        lenient().when(templatesRepo.save(any(Templates.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Templates draftTemplate(UUID id, String key, int version) {
        Templates t = new Templates();
        t.setId(id);
        t.setTemplateKey(key);
        t.setVersion(version);
        t.setName("Plantilla " + key);
        t.setIsActive(false);
        t.setCreatedAt(OffsetDateTime.now());
        return t;
    }

    // ── CRUD ─────────────────────────────────────────────────────────────────────

    @Test
    void create_creaTemplateEnDraftConVersion1() {
        UUID domainId = UUID.randomUUID();
        Domains domain = new Domains();
        domain.setId(domainId);
        domain.setActive(true);
        when(domainsRepo.findById(domainId)).thenReturn(Optional.of(domain));

        CreateTemplateRequest req = new CreateTemplateRequest();
        req.setDomainId(domainId);
        req.setTemplateKey("consent_pagos");
        req.setName("Consentimiento pagos");

        TemplateResponse response = service.create(req);

        assertThat(response.getVersion()).isEqualTo(1);
        assertThat(response.getIsActive()).isFalse();
        assertThat(response.getStatus()).isEqualTo("DRAFT");
        // Regla 4: TEMPLATE_KEY siempre en UPPERCASE
        assertThat(response.getTemplateKey()).isEqualTo("CONSENT_PAGOS");
    }

    @Test
    void newVersion_incrementaVersionManteniendoTemplateKey() {
        UUID sourceId = UUID.randomUUID();
        Templates source = draftTemplate(sourceId, "CONSENT_X", 2);
        source.setIsActive(true);
        when(templatesRepo.findById(sourceId)).thenReturn(Optional.of(source));
        when(templatesRepo.findByDomainIdAndTemplateKeyOrderByVersionDesc(any(), any()))
                .thenReturn(List.of(source));

        TemplateResponse response = service.newVersion(sourceId);

        assertThat(response.getTemplateKey()).isEqualTo("CONSENT_X");
        assertThat(response.getVersion()).isEqualTo(3);
        assertThat(response.getIsActive()).isFalse();
    }

    @Test
    void getById_lanzaTemplateNotFoundException_siNoExiste() {
        UUID id = UUID.randomUUID();
        when(templatesRepo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id))
                .isInstanceOf(TemplateNotFoundException.class);
    }

    @Test
    void getActive_lanzaBusinessValidationException_siNoHayVersionActiva() {
        when(templatesRepo.findByDomainIdAndTemplateKeyAndIsActiveTrue(any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getActive(null, "CONSENT_X"))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void getActive_devuelveLaVersionActiva() {
        Templates active = draftTemplate(UUID.randomUUID(), "CONSENT_X", 2);
        active.setIsActive(true);
        when(templatesRepo.findByDomainIdAndTemplateKeyAndIsActiveTrue(any(), any()))
                .thenReturn(Optional.of(active));

        TemplateResponse response = service.getActive(null, "CONSENT_X");

        assertThat(response.getStatus()).isEqualTo("ACTIVE");
    }

    // ── WORKFLOW: approve ────────────────────────────────────────────────────────

    @Test
    void approve_apruebaTemplateConPurposeVisible() {
        UUID id = UUID.randomUUID();
        Templates template = draftTemplate(id, "CONSENT_X", 1);
        when(templatesRepo.findById(id)).thenReturn(Optional.of(template));
        when(templatePurposesRepo.existsByTemplate_IdAndIsVisibleTrue(id)).thenReturn(true);

        TemplateResponse response = service.approve(id);

        assertThat(response.getApprovedBy()).isEqualTo(actorKeycloakId);
        assertThat(response.getStatus()).isEqualTo("APPROVED");
    }

    @Test
    void approve_lanzaBusinessValidationException_sinPurposeVisible() {
        UUID id = UUID.randomUUID();
        Templates template = draftTemplate(id, "CONSENT_X", 1);
        when(templatesRepo.findById(id)).thenReturn(Optional.of(template));
        when(templatePurposesRepo.existsByTemplate_IdAndIsVisibleTrue(id)).thenReturn(false);

        assertThatThrownBy(() -> service.approve(id))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void approve_esIdempotente_siYaTieneApprovedBy() {
        UUID id = UUID.randomUUID();
        Templates template = draftTemplate(id, "CONSENT_X", 1);
        template.setApprovedBy(UUID.randomUUID().toString());
        template.setApprovedAt(OffsetDateTime.now());
        when(templatesRepo.findById(id)).thenReturn(Optional.of(template));

        TemplateResponse response = service.approve(id);

        assertThat(response.getApprovedBy()).isEqualTo(template.getApprovedBy());
        verify(templatePurposesRepo, never()).existsByTemplate_IdAndIsVisibleTrue(any());
        verify(templatesRepo, never()).save(any());
    }

    // ── WORKFLOW: activate ───────────────────────────────────────────────────────

    @Test
    void activate_activaTemplateYDesactivaVersionAnterior() {
        UUID id = UUID.randomUUID();
        Templates template = draftTemplate(id, "CONSENT_X", 2);
        template.setApprovedBy(UUID.randomUUID().toString());

        UUID previousId = UUID.randomUUID();
        Templates previous = draftTemplate(previousId, "CONSENT_X", 1);
        previous.setIsActive(true);
        previous.setHashSha256("hash-anterior");

        when(templatesRepo.findById(id)).thenReturn(Optional.of(template));
        when(templatesRepo.findByDomainIdAndTemplateKeyOrderByVersionDesc(any(), any()))
                .thenReturn(List.of(previous, template));
        when(templatePurposesRepo.existsByTemplate_IdAndIsVisibleTrue(id)).thenReturn(true);
        when(templatesRepo.findByDomainIdAndTemplateKeyAndIsActiveTrue(any(), any()))
                .thenReturn(Optional.of(previous));
        when(templatePurposesRepo.findByTemplate_IdOrderByOrderPosition(id))
                .thenReturn(List.of());

        TemplateResponse response = service.activate(id, false);

        assertThat(response.getIsActive()).isTrue();
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        assertThat(previous.getIsActive()).isFalse();
        assertThat(template.getPreviousHashSha256()).isEqualTo("hash-anterior");
        verify(templatesRepo, times(2)).save(any(Templates.class));
    }

    @Test
    void activate_lanzaBusinessValidationException_siYaEstaActivo() {
        UUID id = UUID.randomUUID();
        Templates template = draftTemplate(id, "CONSENT_X", 1);
        template.setIsActive(true);
        when(templatesRepo.findById(id)).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.activate(id, false))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void activate_lanzaBusinessValidationException_siVersionEsObsoleta() {
        UUID id = UUID.randomUUID();
        Templates template = draftTemplate(id, "CONSENT_X", 1);
        template.setApprovedBy(UUID.randomUUID().toString());

        Templates newer = draftTemplate(UUID.randomUUID(), "CONSENT_X", 2);

        when(templatesRepo.findById(id)).thenReturn(Optional.of(template));
        when(templatesRepo.findByDomainIdAndTemplateKeyOrderByVersionDesc(any(), any()))
                .thenReturn(List.of(template, newer));

        assertThatThrownBy(() -> service.activate(id, false))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void activate_lanzaBusinessValidationException_sinApprovedBy() {
        UUID id = UUID.randomUUID();
        Templates template = draftTemplate(id, "CONSENT_X", 1);

        when(templatesRepo.findById(id)).thenReturn(Optional.of(template));
        when(templatesRepo.findByDomainIdAndTemplateKeyOrderByVersionDesc(any(), any()))
                .thenReturn(List.of(template));

        assertThatThrownBy(() -> service.activate(id, false))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void activate_lanzaBusinessValidationException_sinPurposeVisible() {
        UUID id = UUID.randomUUID();
        Templates template = draftTemplate(id, "CONSENT_X", 1);
        template.setApprovedBy(UUID.randomUUID().toString());

        when(templatesRepo.findById(id)).thenReturn(Optional.of(template));
        when(templatesRepo.findByDomainIdAndTemplateKeyOrderByVersionDesc(any(), any()))
                .thenReturn(List.of(template));
        when(templatePurposesRepo.existsByTemplate_IdAndIsVisibleTrue(id)).thenReturn(false);

        assertThatThrownBy(() -> service.activate(id, false))
                .isInstanceOf(BusinessValidationException.class);
    }

    // ── PURPOSES ─────────────────────────────────────────────────────────────────

    private Purposes approvedActivePurpose(UUID id) {
        Purposes p = new Purposes();
        p.setId(id);
        p.setName("Marketing");
        p.setIsActive(true);
        p.setApprovedBy(UUID.randomUUID().toString()); // String en arquitectura Keycloak-first
        return p;
    }

    @Test
    void addPurpose_vinculaPurposeAprobadaYActiva() {
        UUID templateId = UUID.randomUUID();
        Templates template = draftTemplate(templateId, "CONSENT_X", 1);
        UUID purposeId = UUID.randomUUID();

        AddTemplatePurposeRequest req = new AddTemplatePurposeRequest();
        req.setPurposeId(purposeId);
        req.setOrderPosition(1);
        req.setIsVisible(true);

        when(templatesRepo.findById(templateId)).thenReturn(Optional.of(template));
        when(templatePurposesRepo.existsByTemplate_IdAndOrderPosition(templateId, 1)).thenReturn(false);
        when(purposesRepo.findById(purposeId)).thenReturn(Optional.of(approvedActivePurpose(purposeId)));

        service.addPurpose(templateId, req);

        verify(templatePurposesRepo, times(1)).save(any(TemplatePurposes.class));
    }

    @Test
    void addPurpose_lanzaBusinessValidationException_siTemplateActivo() {
        UUID templateId = UUID.randomUUID();
        Templates template = draftTemplate(templateId, "CONSENT_X", 1);
        template.setIsActive(true);

        AddTemplatePurposeRequest req = new AddTemplatePurposeRequest();
        req.setPurposeId(UUID.randomUUID());
        req.setOrderPosition(1);

        when(templatesRepo.findById(templateId)).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.addPurpose(templateId, req))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void addPurpose_lanzaBusinessValidationException_siOrderPositionDuplicado() {
        UUID templateId = UUID.randomUUID();
        Templates template = draftTemplate(templateId, "CONSENT_X", 1);

        AddTemplatePurposeRequest req = new AddTemplatePurposeRequest();
        req.setPurposeId(UUID.randomUUID());
        req.setOrderPosition(1);

        when(templatesRepo.findById(templateId)).thenReturn(Optional.of(template));
        when(templatePurposesRepo.existsByTemplate_IdAndOrderPosition(templateId, 1)).thenReturn(true);

        assertThatThrownBy(() -> service.addPurpose(templateId, req))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void addPurpose_lanzaBusinessValidationException_siPurposeNoAprobadaOInactiva() {
        UUID templateId = UUID.randomUUID();
        Templates template = draftTemplate(templateId, "CONSENT_X", 1);
        UUID purposeId = UUID.randomUUID();

        AddTemplatePurposeRequest req = new AddTemplatePurposeRequest();
        req.setPurposeId(purposeId);
        req.setOrderPosition(1);

        Purposes draftPurpose = new Purposes();
        draftPurpose.setId(purposeId);
        draftPurpose.setIsActive(false);

        when(templatesRepo.findById(templateId)).thenReturn(Optional.of(template));
        when(templatePurposesRepo.existsByTemplate_IdAndOrderPosition(templateId, 1)).thenReturn(false);
        when(purposesRepo.findById(purposeId)).thenReturn(Optional.of(draftPurpose));

        assertThatThrownBy(() -> service.addPurpose(templateId, req))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void removePurpose_desvinculaPurposeEnDraft() {
        UUID templateId = UUID.randomUUID();
        UUID purposeId = UUID.randomUUID();
        Templates template = draftTemplate(templateId, "CONSENT_X", 1);

        TemplatePurposes link = new TemplatePurposes();
        when(templatesRepo.findById(templateId)).thenReturn(Optional.of(template));
        when(templatePurposesRepo.findByTemplate_IdAndPurpose_Id(templateId, purposeId))
                .thenReturn(Optional.of(link));

        service.removePurpose(templateId, purposeId);

        verify(templatePurposesRepo, times(1)).deleteByTemplate_IdAndPurpose_Id(templateId, purposeId);
    }

    @Test
    void removePurpose_lanzaBusinessValidationException_siTemplateNoEsDraft() {
        UUID templateId = UUID.randomUUID();
        Templates template = draftTemplate(templateId, "CONSENT_X", 1);
        template.setIsActive(true);

        when(templatesRepo.findById(templateId)).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.removePurpose(templateId, UUID.randomUUID()))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void removePurpose_lanzaBusinessValidationException_siPurposeNoVinculada() {
        UUID templateId = UUID.randomUUID();
        UUID purposeId = UUID.randomUUID();
        Templates template = draftTemplate(templateId, "CONSENT_X", 1);

        when(templatesRepo.findById(templateId)).thenReturn(Optional.of(template));
        when(templatePurposesRepo.findByTemplate_IdAndPurpose_Id(templateId, purposeId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removePurpose(templateId, purposeId))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void updatePurpose_actualizaOrderPositionEIsVisible() {
        UUID templateId = UUID.randomUUID();
        UUID purposeId = UUID.randomUUID();
        Templates template = draftTemplate(templateId, "CONSENT_X", 1);

        TemplatePurposes link = new TemplatePurposes();
        link.setOrderPosition(1);
        link.setIsVisible(true);
        link.setPurpose(approvedActivePurpose(purposeId));

        UpdateTemplatePurposeRequest req = new UpdateTemplatePurposeRequest();
        req.setOrderPosition(2);
        req.setIsVisible(false);

        when(templatesRepo.findById(templateId)).thenReturn(Optional.of(template));
        when(templatePurposesRepo.findByTemplate_IdAndPurpose_Id(templateId, purposeId))
                .thenReturn(Optional.of(link));
        when(templatePurposesRepo.existsByTemplate_IdAndOrderPosition(templateId, 2)).thenReturn(false);
        when(templatePurposesRepo.save(any(TemplatePurposes.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.updatePurpose(templateId, purposeId, req);

        assertThat(response.getOrderPosition()).isEqualTo(2);
        assertThat(response.getIsVisible()).isFalse();
    }

    @Test
    void updatePurpose_lanzaBusinessValidationException_siOrderPositionYaOcupado() {
        UUID templateId = UUID.randomUUID();
        UUID purposeId = UUID.randomUUID();
        Templates template = draftTemplate(templateId, "CONSENT_X", 1);

        TemplatePurposes link = new TemplatePurposes();
        link.setOrderPosition(1);
        link.setIsVisible(true);

        UpdateTemplatePurposeRequest req = new UpdateTemplatePurposeRequest();
        req.setOrderPosition(3);

        when(templatesRepo.findById(templateId)).thenReturn(Optional.of(template));
        when(templatePurposesRepo.findByTemplate_IdAndPurpose_Id(templateId, purposeId))
                .thenReturn(Optional.of(link));
        when(templatePurposesRepo.existsByTemplate_IdAndOrderPosition(templateId, 3)).thenReturn(true);

        assertThatThrownBy(() -> service.updatePurpose(templateId, purposeId, req))
                .isInstanceOf(BusinessValidationException.class);
    }
}
