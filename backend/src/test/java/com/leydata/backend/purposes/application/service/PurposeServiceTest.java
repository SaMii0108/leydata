package com.leydata.backend.purposes.application.service;

import com.leydata.backend.agreement.infrastructure.persistence.AgreementsRepository;
import com.leydata.backend.audit.application.service.AuditService;
import com.leydata.backend.entity.Domains;
import com.leydata.backend.entity.LegalBasisCatalog;
import com.leydata.backend.entity.Purposes;
import com.leydata.backend.entity.Templates;
import com.leydata.backend.entity.TemplatePurposes;
import com.leydata.backend.legalbasis.infrastructure.persistence.LegalBasisRepository;
import com.leydata.backend.orgdomain.infrastructure.persistence.DomainsRepository;
import com.leydata.backend.privacydoc.domain.exception.BusinessValidationException;
import com.leydata.backend.privacydoc.infrastructure.persistence.DocumentPurposesRepository;
import com.leydata.backend.purposes.application.dto.CreatePurposeRequest;
import com.leydata.backend.purposes.application.dto.PurposeResponse;
import com.leydata.backend.purposes.application.dto.UpdatePurposeRequest;
import com.leydata.backend.purposes.domain.exception.PurposeNotFoundException;
import com.leydata.backend.purposes.domain.exception.PurposeNotLockedException;
import com.leydata.backend.purposes.infrastructure.persistence.PurposesRepository;
import com.leydata.backend.purposedatacategory.infrastructure.persistence.PurposeDataCategoryRepository;
import com.leydata.backend.shared.SecurityContextHelper;
import com.leydata.backend.template.infrastructure.persistence.TemplatePurposesRepository;
import com.leydata.backend.userdomain.infrastructure.persistence.UserDomainRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurposeServiceTest {

    @Mock private PurposesRepository purposesRepo;
    @Mock private LegalBasisRepository legalBasisRepo;
    @Mock private DomainsRepository domainsRepo;
    @Mock private DocumentPurposesRepository documentPurposesRepo;
    @Mock private PurposeDataCategoryRepository pdcRepo;
    @Mock private AuditService auditService;
    @Mock private SecurityContextHelper securityContextHelper;
    @Mock private UserDomainRepository userDomainRepository;
    @Mock private TemplatePurposesRepository templatePurposesRepo;
    @Mock private AgreementsRepository agreementsRepo;

    @InjectMocks
    private PurposeService service;

    private final String actorKeycloakId = UUID.randomUUID().toString();
    private final UUID legalBasisId = UUID.randomUUID();
    private final UUID domainId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(securityContextHelper.getKeycloakId()).thenReturn(actorKeycloakId);
        lenient().when(securityContextHelper.getName()).thenReturn("Actor de Prueba");
        lenient().when(securityContextHelper.getActorRole()).thenReturn("DPO");
        lenient().when(purposesRepo.save(any(Purposes.class))).thenAnswer(inv -> inv.getArgument(0));
        // Por defecto ninguna purpose está bloqueada — los tests de lock la sobreescriben
        lenient().when(documentPurposesRepo.existsByPurpose_IdAndDocument_StatusAndIsActiveTrue(any(), any()))
                .thenReturn(false);
        lenient().when(templatePurposesRepo.findByPurpose_Id(any())).thenReturn(List.of());
    }

    private CreatePurposeRequest createRequest() {
        CreatePurposeRequest req = new CreatePurposeRequest();
        req.setCode("marketing_email");
        req.setName("Marketing por email");
        req.setDescription("Uso del email para enviar ofertas");
        req.setShortDescription("Marketing");
        req.setRequired(false);
        req.setRevocable(true);
        req.setLegalBasisId(legalBasisId);
        req.setDomainId(domainId);
        return req;
    }

    private Domains activeDomain() {
        Domains d = new Domains();
        d.setId(domainId);
        d.setName("Ventas");
        d.setActive(true);
        return d;
    }

    private Purposes purpose(UUID id, UUID familyId, int version, String status) {
        Purposes p = new Purposes();
        p.setId(id);
        p.setCode("MARKETING_EMAIL");
        p.setName("Marketing por email");
        p.setDescription("Uso del email para enviar ofertas");
        p.setShortDescription("Marketing");
        p.setRequired(false);
        p.setRevocable(true);
        p.setLegalBasisId(legalBasisId);
        p.setDomainId(domainId);
        p.setPurposeFamilyId(familyId);
        p.setVersion(version);
        p.setStatus(status);
        return p;
    }

    // ── create() ─────────────────────────────────────────────────────────────────

    @Test
    void create_creaPurposeConVersion1YFamilyIdAutoreferenciado() {
        when(purposesRepo.existsByCode("MARKETING_EMAIL")).thenReturn(false);
        when(legalBasisRepo.findById(legalBasisId)).thenReturn(Optional.of(new LegalBasisCatalog()));
        when(domainsRepo.findById(domainId)).thenReturn(Optional.of(activeDomain()));

        PurposeResponse response = service.create(createRequest());

        assertThat(response.version()).isEqualTo(1);
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.purposeFamilyId()).isEqualTo(response.id());
        assertThat(response.hashSha256()).isNotBlank();
    }

    @Test
    void create_lanzaBusinessValidationException_siCodigoDuplicado() {
        when(purposesRepo.existsByCode("MARKETING_EMAIL")).thenReturn(true);

        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void create_lanzaBusinessValidationException_siLegalBasisNoExiste() {
        when(purposesRepo.existsByCode("MARKETING_EMAIL")).thenReturn(false);
        when(legalBasisRepo.findById(legalBasisId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void create_lanzaBusinessValidationException_siDominioDesactivado() {
        when(purposesRepo.existsByCode("MARKETING_EMAIL")).thenReturn(false);
        when(legalBasisRepo.findById(legalBasisId)).thenReturn(Optional.of(new LegalBasisCatalog()));
        Domains inactive = activeDomain();
        inactive.setActive(false);
        when(domainsRepo.findById(domainId)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(BusinessValidationException.class);
    }

    // ── update() / lock unificado (documento o template+agreement) ─────────────────

    @Test
    void update_actualizaCamposCuandoNoEstaBloqueada() {
        UUID id = UUID.randomUUID();
        Purposes purpose = purpose(id, id, 1, "ACTIVE");
        when(purposesRepo.findById(id)).thenReturn(Optional.of(purpose));

        UpdatePurposeRequest req = new UpdatePurposeRequest();
        req.setName("Nuevo nombre");

        PurposeResponse response = service.update(id, req);

        assertThat(response.name()).isEqualTo("Nuevo nombre");
    }

    @Test
    void update_lanzaBusinessValidationException_siBloqueadaPorDocumentoPublicado() {
        UUID id = UUID.randomUUID();
        Purposes purpose = purpose(id, id, 1, "ACTIVE");
        when(purposesRepo.findById(id)).thenReturn(Optional.of(purpose));
        when(documentPurposesRepo.existsByPurpose_IdAndDocument_StatusAndIsActiveTrue(any(), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.update(id, new UpdatePurposeRequest()))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void update_lanzaBusinessValidationException_siBloqueadaPorTemplateConAgreement() {
        UUID id = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        Purposes purpose = purpose(id, id, 1, "ACTIVE");
        when(purposesRepo.findById(id)).thenReturn(Optional.of(purpose));
        when(templatePurposesRepo.findByPurpose_Id(id)).thenReturn(List.of(linkToTemplate(templateId)));
        when(agreementsRepo.existsByTemplateIdIn(List.of(templateId))).thenReturn(true);

        assertThatThrownBy(() -> service.update(id, new UpdatePurposeRequest()))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void update_noBloqueaSiTemplateVinculadoNoTieneAgreements() {
        UUID id = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        Purposes purpose = purpose(id, id, 1, "ACTIVE");
        when(purposesRepo.findById(id)).thenReturn(Optional.of(purpose));
        when(templatePurposesRepo.findByPurpose_Id(id)).thenReturn(List.of(linkToTemplate(templateId)));
        when(agreementsRepo.existsByTemplateIdIn(List.of(templateId))).thenReturn(false);

        UpdatePurposeRequest req = new UpdatePurposeRequest();
        req.setName("Editado libremente");

        PurposeResponse response = service.update(id, req);

        assertThat(response.name()).isEqualTo("Editado libremente");
    }

    private TemplatePurposes linkToTemplate(UUID templateId) {
        TemplatePurposes tp = new TemplatePurposes();
        Templates t = new Templates();
        t.setId(templateId);
        tp.setTemplate(t);
        return tp;
    }

    // ── deactivate() ─────────────────────────────────────────────────────────────

    @Test
    void deactivate_desactivaCuandoNoTieneCategoriasDeDatos() {
        UUID id = UUID.randomUUID();
        Purposes purpose = purpose(id, id, 1, "ACTIVE");
        purpose.setIsActive(true);
        when(purposesRepo.findById(id)).thenReturn(Optional.of(purpose));
        when(pdcRepo.existsByPurposeId(id)).thenReturn(false);

        PurposeResponse response = service.deactivate(id);

        assertThat(response.isActive()).isFalse();
    }

    @Test
    void deactivate_lanzaBusinessValidationException_siTieneCategoriasDeDatosActivas() {
        UUID id = UUID.randomUUID();
        Purposes purpose = purpose(id, id, 1, "ACTIVE");
        when(purposesRepo.findById(id)).thenReturn(Optional.of(purpose));
        when(pdcRepo.existsByPurposeId(id)).thenReturn(true);

        assertThatThrownBy(() -> service.deactivate(id))
                .isInstanceOf(BusinessValidationException.class);
    }

    // ── newVersion() ─────────────────────────────────────────────────────────────

    @Test
    void newVersion_creaNuevaVersionYSupersedeALaAnterior_cuandoEstaBloqueada() {
        UUID sourceId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        Purposes source = purpose(sourceId, sourceId, 1, "ACTIVE");
        when(purposesRepo.findById(sourceId)).thenReturn(Optional.of(source));
        when(templatePurposesRepo.findByPurpose_Id(sourceId)).thenReturn(List.of(linkToTemplate(templateId)));
        when(agreementsRepo.existsByTemplateIdIn(List.of(templateId))).thenReturn(true);
        when(purposesRepo.findByPurposeFamilyIdOrderByVersionDesc(sourceId)).thenReturn(List.of(source));

        UpdatePurposeRequest req = new UpdatePurposeRequest();
        req.setName("Marketing por email v2");

        PurposeResponse response = service.newVersion(sourceId, req);

        assertThat(response.version()).isEqualTo(2);
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.purposeFamilyId()).isEqualTo(sourceId);
        assertThat(response.name()).isEqualTo("Marketing por email v2");
        assertThat(source.getStatus()).isEqualTo("SUPERSEDED");
    }

    @Test
    void newVersion_lanzaPurposeNotLockedException_siNoEstaBloqueada() {
        UUID sourceId = UUID.randomUUID();
        Purposes source = purpose(sourceId, sourceId, 1, "ACTIVE");
        when(purposesRepo.findById(sourceId)).thenReturn(Optional.of(source));

        assertThatThrownBy(() -> service.newVersion(sourceId, new UpdatePurposeRequest()))
                .isInstanceOf(PurposeNotLockedException.class);
    }

    @Test
    void newVersion_lanzaPurposeNotFoundException_siNoExiste() {
        UUID sourceId = UUID.randomUUID();
        when(purposesRepo.findById(sourceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.newVersion(sourceId, new UpdatePurposeRequest()))
                .isInstanceOf(PurposeNotFoundException.class);
    }

    // ── getFamily() / getActiveByFamily() ───────────────────────────────────────

    @Test
    void getFamily_devuelveVersionesOrdenadasDesc() {
        UUID familyId = UUID.randomUUID();
        Purposes v2 = purpose(UUID.randomUUID(), familyId, 2, "ACTIVE");
        Purposes v1 = purpose(UUID.randomUUID(), familyId, 1, "SUPERSEDED");
        when(purposesRepo.findByPurposeFamilyIdOrderByVersionDesc(familyId)).thenReturn(List.of(v2, v1));

        List<PurposeResponse> result = service.getFamily(familyId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).version()).isEqualTo(2);
        assertThat(result.get(1).version()).isEqualTo(1);
    }

    @Test
    void getActiveByFamily_devuelveLaVersionActive() {
        UUID familyId = UUID.randomUUID();
        Purposes active = purpose(UUID.randomUUID(), familyId, 2, "ACTIVE");
        when(purposesRepo.findByPurposeFamilyIdAndStatus(familyId, "ACTIVE")).thenReturn(Optional.of(active));

        PurposeResponse response = service.getActiveByFamily(familyId);

        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.version()).isEqualTo(2);
    }

    @Test
    void getActiveByFamily_lanzaBusinessValidationException_siNoHayActiva() {
        UUID familyId = UUID.randomUUID();
        when(purposesRepo.findByPurposeFamilyIdAndStatus(familyId, "ACTIVE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getActiveByFamily(familyId))
                .isInstanceOf(BusinessValidationException.class);
    }

    // ── recalculateHash() ────────────────────────────────────────────────────────

    @Test
    void recalculateHash_devuelveElMismoHashParaElMismoContenido() {
        UUID id = UUID.randomUUID();
        Purposes p = purpose(id, id, 1, "ACTIVE");
        when(purposesRepo.findById(id)).thenReturn(Optional.of(p));

        String first = service.recalculateHash(id);
        String second = service.recalculateHash(id);

        assertThat(first).isNotBlank();
        assertThat(first).isEqualTo(second);
    }

    @Test
    void recalculateHash_lanzaPurposeNotFoundException_siNoExiste() {
        UUID id = UUID.randomUUID();
        when(purposesRepo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recalculateHash(id))
                .isInstanceOf(PurposeNotFoundException.class);
    }
}
