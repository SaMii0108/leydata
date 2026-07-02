package com.leydata.backend.purposes.application.service;

import com.leydata.backend.audit.application.service.AuditService;
import com.leydata.backend.agreement.infrastructure.persistence.AgreementsRepository;
import com.leydata.backend.entity.Domains;
import com.leydata.backend.entity.Purposes;
import com.leydata.backend.entity.TemplatePurposes;
import com.leydata.backend.entity.Templates;
import com.leydata.backend.legalbasis.infrastructure.persistence.LegalBasisRepository;
import com.leydata.backend.entity.LegalBasisCatalog;
import com.leydata.backend.orgdomain.infrastructure.persistence.DomainsRepository;
import com.leydata.backend.privacydoc.domain.enums.DocumentStatus;
import com.leydata.backend.privacydoc.domain.exception.BusinessValidationException;
import com.leydata.backend.privacydoc.infrastructure.persistence.DocumentPurposesRepository;
import com.leydata.backend.purposedatacategory.infrastructure.persistence.PurposeDataCategoryRepository;
import com.leydata.backend.purposes.application.dto.CreatePurposeRequest;
import com.leydata.backend.purposes.application.dto.PurposeResponse;
import com.leydata.backend.purposes.application.dto.UpdatePurposeRequest;
import com.leydata.backend.purposes.domain.exception.PurposeNotFoundException;
import com.leydata.backend.purposes.domain.exception.PurposeNotLockedException;
import com.leydata.backend.purposes.infrastructure.persistence.PurposesRepository;
import com.leydata.backend.shared.SecurityContextHelper;
import com.leydata.backend.template.infrastructure.persistence.TemplatePurposesRepository;
import com.leydata.backend.userdomain.domain.UserDomain;
import com.leydata.backend.userdomain.infrastructure.persistence.UserDomainRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

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

    private final UUID domainId = UUID.randomUUID();
    private final UUID legalBasisId = UUID.randomUUID();
    private final UUID purposeId = UUID.randomUUID();
    private final String actorId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        lenient().when(securityContextHelper.getKeycloakId()).thenReturn(actorId);
        lenient().when(securityContextHelper.getName()).thenReturn("DPO Test");
        lenient().when(securityContextHelper.getActorRole()).thenReturn("DPO");
        lenient().when(purposesRepo.save(any(Purposes.class))).thenAnswer(inv -> {
            Purposes p = inv.getArgument(0);
            if (p.getId() == null) p.setId(purposeId);
            return p;
        });
        lenient().when(documentPurposesRepo.existsByPurpose_IdAndDocument_StatusAndIsActiveTrue(any(), any()))
                .thenReturn(false);
        lenient().when(templatePurposesRepo.findByPurpose_Id(any())).thenReturn(List.of());
    }

    private Domains activeDomain() {
        Domains d = new Domains();
        d.setId(domainId);
        d.setName("Marketing");
        d.setActive(true);
        return d;
    }

    private CreatePurposeRequest createRequest() {
        CreatePurposeRequest req = new CreatePurposeRequest();
        req.setCode("marketing_newsletter");
        req.setName("Newsletter");
        req.setDescription("Envío de newsletters");
        req.setRequired(false);
        req.setRevocable(true);
        req.setLegalBasisId(legalBasisId);
        req.setDomainId(domainId);
        return req;
    }

    private Purposes purpose(boolean isActive) {
        Purposes p = new Purposes();
        p.setId(purposeId);
        p.setCode("MARKETING_NEWSLETTER");
        p.setName("Newsletter");
        p.setDescription("Envío de newsletters");
        p.setRequired(false);
        p.setRevocable(true);
        p.setLegalBasisId(legalBasisId);
        p.setDomainId(domainId);
        p.setIsActive(isActive);
        p.setVersion(1);
        p.setStatus("ACTIVE");
        p.setPurposeFamilyId(purposeId);
        return p;
    }

    private void mockUnlocked() {
        lenient().when(documentPurposesRepo.existsByPurpose_IdAndDocument_StatusAndIsActiveTrue(purposeId, DocumentStatus.PUBLISHED))
                .thenReturn(false);
        lenient().when(templatePurposesRepo.findByPurpose_Id(purposeId)).thenReturn(List.of());
    }

    private void mockLockedByDocument() {
        when(documentPurposesRepo.existsByPurpose_IdAndDocument_StatusAndIsActiveTrue(purposeId, DocumentStatus.PUBLISHED))
                .thenReturn(true);
    }

    // ── create() ─────────────────────────────────────────────────────────────────

    @Test
    void create_creaFinalidadEnVersion1ActivaConHashYPurposeFamilyIdPropio() {
        when(purposesRepo.existsByCode("MARKETING_NEWSLETTER")).thenReturn(false);
        when(legalBasisRepo.findById(legalBasisId)).thenReturn(Optional.of(new LegalBasisCatalog()));
        when(domainsRepo.findById(domainId)).thenReturn(Optional.of(activeDomain()));

        PurposeResponse response = service.create(createRequest());

        assertThat(response.version()).isEqualTo(1);
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.code()).isEqualTo("MARKETING_NEWSLETTER");
        assertThat(response.purposeFamilyId()).isEqualTo(purposeId);
        assertThat(response.hashSha256()).isNotBlank();
    }

    @Test
    void create_lanzaExcepcion_siElCodigoYaExiste() {
        when(purposesRepo.existsByCode("MARKETING_NEWSLETTER")).thenReturn(true);

        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void create_lanzaExcepcion_siLaBaseDeLicitudNoExiste() {
        when(purposesRepo.existsByCode("MARKETING_NEWSLETTER")).thenReturn(false);
        when(legalBasisRepo.findById(legalBasisId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void create_lanzaExcepcion_siElDominioNoExiste() {
        when(purposesRepo.existsByCode("MARKETING_NEWSLETTER")).thenReturn(false);
        when(legalBasisRepo.findById(legalBasisId)).thenReturn(Optional.of(new LegalBasisCatalog()));
        when(domainsRepo.findById(domainId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void create_lanzaExcepcion_siElDominioEstaDesactivado() {
        Domains inactive = activeDomain();
        inactive.setActive(false);
        when(purposesRepo.existsByCode("MARKETING_NEWSLETTER")).thenReturn(false);
        when(legalBasisRepo.findById(legalBasisId)).thenReturn(Optional.of(new LegalBasisCatalog()));
        when(domainsRepo.findById(domainId)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(BusinessValidationException.class);
    }

    // ── listAll() ────────────────────────────────────────────────────────────────

    @Test
    void listAll_paraAdminODpo_devuelveTodasLasActivas() {
        when(purposesRepo.findByIsActiveTrue()).thenReturn(List.of(purpose(true)));

        List<PurposeResponse> result = service.listAll();

        assertThat(result).hasSize(1);
    }

    @Test
    void listAll_paraJefeDominio_devuelveSoloLasDeSusDominiosAsignados() {
        when(securityContextHelper.getActorRole()).thenReturn("JEFE_DOMINIO");
        UserDomain ud = UserDomain.builder().keycloakId(actorId).domain(activeDomain()).build();
        when(userDomainRepository.findByKeycloakId(actorId)).thenReturn(List.of(ud));
        when(purposesRepo.findByDomainIdAndIsActiveTrue(domainId)).thenReturn(List.of(purpose(true)));

        List<PurposeResponse> result = service.listAll();

        assertThat(result).hasSize(1);
    }

    // ── getById() ────────────────────────────────────────────────────────────────

    @Test
    void getById_devuelveLaFinalidadConIsLockedCalculado() {
        when(purposesRepo.findById(purposeId)).thenReturn(Optional.of(purpose(true)));
        mockUnlocked();

        PurposeResponse response = service.getById(purposeId);

        assertThat(response.locked()).isFalse();
    }

    @Test
    void getById_lanzaExcepcion_siNoExiste() {
        when(purposesRepo.findById(purposeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(purposeId))
                .isInstanceOf(PurposeNotFoundException.class);
    }

    @Test
    void getById_jefeDominioSinAccesoAlDominio_lanzaAccessDeniedException() {
        when(securityContextHelper.getActorRole()).thenReturn("JEFE_DOMINIO");
        when(purposesRepo.findById(purposeId)).thenReturn(Optional.of(purpose(true)));
        when(userDomainRepository.existsByKeycloakIdAndDomainId(actorId, domainId)).thenReturn(false);

        assertThatThrownBy(() -> service.getById(purposeId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getById_jefeDominioConAccesoAlDominio_devuelveLaFinalidad() {
        when(securityContextHelper.getActorRole()).thenReturn("JEFE_DOMINIO");
        when(purposesRepo.findById(purposeId)).thenReturn(Optional.of(purpose(true)));
        when(userDomainRepository.existsByKeycloakIdAndDomainId(actorId, domainId)).thenReturn(true);
        mockUnlocked();

        PurposeResponse response = service.getById(purposeId);

        assertThat(response.id()).isEqualTo(purposeId);
    }

    // ── listByDomain() ───────────────────────────────────────────────────────────

    @Test
    void listByDomain_jefeDominioSinAcceso_lanzaAccessDeniedException() {
        when(securityContextHelper.getActorRole()).thenReturn("JEFE_DOMINIO");
        when(userDomainRepository.existsByKeycloakIdAndDomainId(actorId, domainId)).thenReturn(false);

        assertThatThrownBy(() -> service.listByDomain(domainId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void listByDomain_adminODpo_puedeVerCualquierDominio() {
        when(purposesRepo.findByDomainIdAndIsActiveTrue(domainId)).thenReturn(List.of(purpose(true)));

        List<PurposeResponse> result = service.listByDomain(domainId);

        assertThat(result).hasSize(1);
    }

    // ── update() ─────────────────────────────────────────────────────────────────

    @Test
    void update_actualizaCamposEnviadosYRecalculaHash() {
        when(purposesRepo.findById(purposeId)).thenReturn(Optional.of(purpose(true)));
        mockUnlocked();

        UpdatePurposeRequest req = new UpdatePurposeRequest();
        req.setName("Newsletter actualizado");

        PurposeResponse response = service.update(purposeId, req);

        assertThat(response.name()).isEqualTo("Newsletter actualizado");
    }

    @Test
    void update_lanzaExcepcion_siLaFinalidadEstaBloqueada() {
        when(purposesRepo.findById(purposeId)).thenReturn(Optional.of(purpose(true)));
        mockLockedByDocument();

        assertThatThrownBy(() -> service.update(purposeId, new UpdatePurposeRequest()))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void update_lanzaExcepcion_siElNuevoLegalBasisIdNoExiste() {
        when(purposesRepo.findById(purposeId)).thenReturn(Optional.of(purpose(true)));
        mockUnlocked();
        UUID otroLegalBasis = UUID.randomUUID();
        when(legalBasisRepo.findById(otroLegalBasis)).thenReturn(Optional.empty());

        UpdatePurposeRequest req = new UpdatePurposeRequest();
        req.setLegalBasisId(otroLegalBasis);

        assertThatThrownBy(() -> service.update(purposeId, req))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void update_camposNulos_noSobrescribenLosExistentes() {
        when(purposesRepo.findById(purposeId)).thenReturn(Optional.of(purpose(true)));
        mockUnlocked();

        PurposeResponse response = service.update(purposeId, new UpdatePurposeRequest());

        assertThat(response.name()).isEqualTo("Newsletter");
        assertThat(response.description()).isEqualTo("Envío de newsletters");
    }

    // ── deactivate() ─────────────────────────────────────────────────────────────

    @Test
    void deactivate_desactivaCorrectamente() {
        when(purposesRepo.findById(purposeId)).thenReturn(Optional.of(purpose(true)));
        mockUnlocked();
        when(pdcRepo.existsByPurposeId(purposeId)).thenReturn(false);

        PurposeResponse response = service.deactivate(purposeId);

        assertThat(response.isActive()).isFalse();
    }

    @Test
    void deactivate_lanzaExcepcion_siLaFinalidadEstaBloqueada() {
        when(purposesRepo.findById(purposeId)).thenReturn(Optional.of(purpose(true)));
        mockLockedByDocument();

        assertThatThrownBy(() -> service.deactivate(purposeId))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void deactivate_lanzaExcepcion_siTieneCategoriasDeDatosVinculadas() {
        when(purposesRepo.findById(purposeId)).thenReturn(Optional.of(purpose(true)));
        mockUnlocked();
        when(pdcRepo.existsByPurposeId(purposeId)).thenReturn(true);

        assertThatThrownBy(() -> service.deactivate(purposeId))
                .isInstanceOf(BusinessValidationException.class);
    }

    // ── newVersion() ─────────────────────────────────────────────────────────────

    @Test
    void newVersion_creaNuevaVersionHeredaCamposYMarcaElOrigenComoSuperseded() {
        Purposes source = purpose(true);
        when(purposesRepo.findById(purposeId)).thenReturn(Optional.of(source));
        mockLockedByDocument();
        when(purposesRepo.findByPurposeFamilyIdOrderByVersionDesc(purposeId)).thenReturn(List.of(source));

        UpdatePurposeRequest req = new UpdatePurposeRequest();
        req.setName("Newsletter v2");

        PurposeResponse response = service.newVersion(purposeId, req);

        assertThat(response.version()).isEqualTo(2);
        assertThat(response.name()).isEqualTo("Newsletter v2");
        assertThat(response.purposeFamilyId()).isEqualTo(purposeId);
        assertThat(source.getStatus()).isEqualTo("SUPERSEDED");
    }

    @Test
    void newVersion_heredaCamposNoEnviadosDelOrigen() {
        Purposes source = purpose(true);
        when(purposesRepo.findById(purposeId)).thenReturn(Optional.of(source));
        mockLockedByDocument();
        when(purposesRepo.findByPurposeFamilyIdOrderByVersionDesc(purposeId)).thenReturn(List.of(source));

        PurposeResponse response = service.newVersion(purposeId, new UpdatePurposeRequest());

        assertThat(response.name()).isEqualTo(source.getName());
        assertThat(response.description()).isEqualTo(source.getDescription());
    }

    @Test
    void newVersion_lanzaExcepcion_siElOrigenNoEstaBloqueado() {
        when(purposesRepo.findById(purposeId)).thenReturn(Optional.of(purpose(true)));
        mockUnlocked();

        assertThatThrownBy(() -> service.newVersion(purposeId, new UpdatePurposeRequest()))
                .isInstanceOf(PurposeNotLockedException.class);
    }

    // ── getFamily() ──────────────────────────────────────────────────────────────

    @Test
    void getFamily_devuelveElHistorialDeVersiones() {
        when(purposesRepo.findByPurposeFamilyIdOrderByVersionDesc(purposeId)).thenReturn(List.of(purpose(true)));
        mockUnlocked();

        List<PurposeResponse> result = service.getFamily(purposeId);

        assertThat(result).hasSize(1);
    }

    // ── getActiveByFamily() ──────────────────────────────────────────────────────

    @Test
    void getActiveByFamily_devuelveLaVersionActiva() {
        when(purposesRepo.findByPurposeFamilyIdAndStatus(purposeId, "ACTIVE")).thenReturn(Optional.of(purpose(true)));
        mockUnlocked();

        PurposeResponse response = service.getActiveByFamily(purposeId);

        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void getActiveByFamily_lanzaExcepcion_siNoHayVersionActiva() {
        when(purposesRepo.findByPurposeFamilyIdAndStatus(purposeId, "ACTIVE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getActiveByFamily(purposeId))
                .isInstanceOf(BusinessValidationException.class);
    }

    // ── recalculateHash() ────────────────────────────────────────────────────────

    @Test
    void recalculateHash_devuelveElHashSinPersistir() {
        when(purposesRepo.findById(purposeId)).thenReturn(Optional.of(purpose(true)));

        String hash = service.recalculateHash(purposeId);

        assertThat(hash).isNotBlank();
    }
}
