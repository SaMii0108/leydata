package com.leydata.backend.purposedatacategory.application.service;

import com.leydata.backend.audit.application.service.AuditService;
import com.leydata.backend.datacategory.infrastructure.persistence.DataCategoryRepository;
import com.leydata.backend.entity.DataCategories;
import com.leydata.backend.entity.DataRetentionPolicies;
import com.leydata.backend.entity.PurposeDataCategories;
import com.leydata.backend.entity.Purposes;
import com.leydata.backend.privacydoc.domain.enums.DocumentStatus;
import com.leydata.backend.privacydoc.domain.exception.BusinessValidationException;
import com.leydata.backend.privacydoc.infrastructure.persistence.DocumentPurposesRepository;
import com.leydata.backend.purposedatacategory.application.dto.DataRetentionPolicyRequest;
import com.leydata.backend.purposedatacategory.application.dto.PurposeDataCategoryRequest;
import com.leydata.backend.purposedatacategory.application.dto.PurposeDataCategoryResponse;
import com.leydata.backend.purposedatacategory.domain.enums.DataUseType;
import com.leydata.backend.purposedatacategory.domain.exception.PurposeDataCategoryNotFoundException;
import com.leydata.backend.purposedatacategory.domain.exception.RetentionPolicyLockedException;
import com.leydata.backend.purposedatacategory.infrastructure.persistence.PurposeDataCategoryRepository;
import com.leydata.backend.purposedatacategory.infrastructure.persistence.RetentionPolicyRepository;
import com.leydata.backend.purposes.infrastructure.persistence.PurposesRepository;
import com.leydata.backend.shared.SecurityContextHelper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurposeDataCategoryServiceTest {

    @Mock private PurposeDataCategoryRepository pdcRepo;
    @Mock private RetentionPolicyRepository retentionRepo;
    @Mock private DataCategoryRepository dataCategoryRepo;
    @Mock private PurposesRepository purposesRepo;
    @Mock private DocumentPurposesRepository documentPurposesRepo;
    @Mock private AuditService auditService;
    @Mock private SecurityContextHelper securityContextHelper;

    @InjectMocks
    private PurposeDataCategoryService service;

    private final UUID purposeId = UUID.randomUUID();
    private final UUID dataCategoryId = UUID.randomUUID();
    private final UUID pdcId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(securityContextHelper.getKeycloakId()).thenReturn("actor-keycloak-id");
        lenient().when(securityContextHelper.getActorRole()).thenReturn("DPO");
        lenient().when(retentionRepo.save(any(DataRetentionPolicies.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Purposes approvedPurpose() {
        Purposes p = new Purposes();
        p.setId(purposeId);
        p.setName("Facturación");
        p.setIsActive(true);
        p.setApprovedBy("dpo-keycloak-id");
        return p;
    }

    private DataCategories activeDataCategory() {
        DataCategories c = new DataCategories();
        c.setId(dataCategoryId);
        c.setCode("FINANCIERO");
        c.setName("Financiero");
        c.setIsActive(true);
        return c;
    }

    private PurposeDataCategoryRequest linkRequest() {
        PurposeDataCategoryRequest req = new PurposeDataCategoryRequest();
        req.setDataCategoryId(dataCategoryId);
        req.setRequired(true);
        req.setDataUses(Set.of(DataUseType.STORAGE));
        req.setRetention(retentionRequest());
        return req;
    }

    private DataRetentionPolicyRequest retentionRequest() {
        DataRetentionPolicyRequest req = new DataRetentionPolicyRequest();
        req.setRetentionPeriod(5);
        req.setRetentionUnit("YEARS");
        req.setLegalJustification("Art. 17 Ley 21.719");
        req.setAnonymizeAfter(false);
        return req;
    }

    private PurposeDataCategories linkedEntity() {
        PurposeDataCategories pdc = new PurposeDataCategories();
        pdc.setId(pdcId);
        pdc.setPurposeId(purposeId);
        pdc.setDataCategoryId(dataCategoryId);
        pdc.setRequired(true);
        pdc.setDataUses(Set.of(DataUseType.STORAGE));
        return pdc;
    }

    /** Configura pdcRepo.save()/findById() para que compartan la misma referencia mutable,
     * igual que haría JPA (necesario porque link() muta `saved` después de guardarlo y
     * luego recupera por findById() para construir la respuesta). */
    private void mockSaveAndFindByIdSharingReference() {
        AtomicReference<PurposeDataCategories> ref = new AtomicReference<>();
        when(pdcRepo.save(any(PurposeDataCategories.class))).thenAnswer(inv -> {
            PurposeDataCategories p = inv.getArgument(0);
            if (p.getId() == null) p.setId(pdcId);
            ref.set(p);
            return p;
        });
        when(pdcRepo.findById(pdcId)).thenAnswer(inv -> Optional.ofNullable(ref.get()));
    }

    private void mockUnlockedDocument() {
        when(documentPurposesRepo.existsByPurpose_IdAndDocument_StatusAndIsActiveTrue(purposeId, DocumentStatus.PUBLISHED))
                .thenReturn(false);
    }

    private void mockLockedDocument() {
        when(documentPurposesRepo.existsByPurpose_IdAndDocument_StatusAndIsActiveTrue(purposeId, DocumentStatus.PUBLISHED))
                .thenReturn(true);
    }

    // ── listByPurpose() ──────────────────────────────────────────────────────────

    @Test
    void listByPurpose_devuelveLosVinculosDeLaFinalidadConRetentionLockedCalculado() {
        when(pdcRepo.findByPurposeId(purposeId)).thenReturn(List.of(linkedEntity()));
        mockUnlockedDocument();

        List<PurposeDataCategoryResponse> result = service.listByPurpose(purposeId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRetentionLocked()).isFalse();
    }

    // ── getById() ────────────────────────────────────────────────────────────────

    @Test
    void getById_devuelveElVinculo() {
        when(pdcRepo.findById(pdcId)).thenReturn(Optional.of(linkedEntity()));
        mockUnlockedDocument();

        PurposeDataCategoryResponse response = service.getById(pdcId);

        assertThat(response.getDataCategoryId()).isEqualTo(dataCategoryId);
    }

    @Test
    void getById_lanzaExcepcion_siNoExiste() {
        when(pdcRepo.findById(pdcId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(pdcId))
                .isInstanceOf(PurposeDataCategoryNotFoundException.class);
    }

    // ── link() ───────────────────────────────────────────────────────────────────

    @Test
    void link_vinculaCategoriaYCreaPoliticaDeRetencion() {
        when(purposesRepo.findById(purposeId)).thenReturn(Optional.of(approvedPurpose()));
        when(dataCategoryRepo.findById(dataCategoryId)).thenReturn(Optional.of(activeDataCategory()));
        mockUnlockedDocument();
        when(pdcRepo.existsByPurposeIdAndDataCategoryId(purposeId, dataCategoryId)).thenReturn(false);
        mockSaveAndFindByIdSharingReference();

        PurposeDataCategoryResponse response = service.link(purposeId, linkRequest());

        assertThat(response.getRetentionLocked()).isFalse();
        verify(retentionRepo).save(any(DataRetentionPolicies.class));
    }

    @Test
    void link_lanzaExcepcion_siLaFinalidadNoExiste() {
        when(purposesRepo.findById(purposeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.link(purposeId, linkRequest()))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void link_lanzaExcepcion_siLaFinalidadNoEstaAprobadaOActiva() {
        Purposes notApproved = approvedPurpose();
        notApproved.setApprovedBy(null);
        when(purposesRepo.findById(purposeId)).thenReturn(Optional.of(notApproved));

        assertThatThrownBy(() -> service.link(purposeId, linkRequest()))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void link_lanzaExcepcion_siLaCategoriaDeDatosNoExiste() {
        when(purposesRepo.findById(purposeId)).thenReturn(Optional.of(approvedPurpose()));
        when(dataCategoryRepo.findById(dataCategoryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.link(purposeId, linkRequest()))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void link_lanzaExcepcion_siLaCategoriaDeDatosEstaInactiva() {
        DataCategories inactive = activeDataCategory();
        inactive.setIsActive(false);
        when(purposesRepo.findById(purposeId)).thenReturn(Optional.of(approvedPurpose()));
        when(dataCategoryRepo.findById(dataCategoryId)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.link(purposeId, linkRequest()))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void link_lanzaExcepcion_siLaFinalidadEstaEnDocumentoPublicado() {
        when(purposesRepo.findById(purposeId)).thenReturn(Optional.of(approvedPurpose()));
        when(dataCategoryRepo.findById(dataCategoryId)).thenReturn(Optional.of(activeDataCategory()));
        mockLockedDocument();

        assertThatThrownBy(() -> service.link(purposeId, linkRequest()))
                .isInstanceOf(RetentionPolicyLockedException.class);
    }

    @Test
    void link_lanzaExcepcion_siLaCategoriaYaEstaVinculada() {
        when(purposesRepo.findById(purposeId)).thenReturn(Optional.of(approvedPurpose()));
        when(dataCategoryRepo.findById(dataCategoryId)).thenReturn(Optional.of(activeDataCategory()));
        mockUnlockedDocument();
        when(pdcRepo.existsByPurposeIdAndDataCategoryId(purposeId, dataCategoryId)).thenReturn(true);

        assertThatThrownBy(() -> service.link(purposeId, linkRequest()))
                .isInstanceOf(BusinessValidationException.class);
    }

    // ── updateRetention() ────────────────────────────────────────────────────────

    @Test
    void updateRetention_actualizaLaPoliticaExistente() {
        PurposeDataCategories pdc = linkedEntity();
        when(pdcRepo.findById(pdcId)).thenReturn(Optional.of(pdc));
        mockUnlockedDocument();

        DataRetentionPolicies existing = new DataRetentionPolicies();
        existing.setPurposeDataCategoryId(pdcId);
        existing.setRetentionPeriod(3);
        existing.setRetentionUnit("YEARS");
        when(retentionRepo.findByPurposeDataCategoryId(pdcId)).thenReturn(Optional.of(existing));

        DataRetentionPolicyRequest newReq = retentionRequest();
        newReq.setRetentionPeriod(10);

        service.updateRetention(pdcId, newReq);

        assertThat(existing.getRetentionPeriod()).isEqualTo(10);
        verify(retentionRepo).save(existing);
    }

    @Test
    void updateRetention_lanzaExcepcion_siLaFinalidadEstaBloqueada() {
        when(pdcRepo.findById(pdcId)).thenReturn(Optional.of(linkedEntity()));
        mockLockedDocument();

        assertThatThrownBy(() -> service.updateRetention(pdcId, retentionRequest()))
                .isInstanceOf(RetentionPolicyLockedException.class);
    }

    @Test
    void updateRetention_lanzaExcepcion_siElVinculoNoExiste() {
        when(pdcRepo.findById(pdcId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateRetention(pdcId, retentionRequest()))
                .isInstanceOf(PurposeDataCategoryNotFoundException.class);
    }

    @Test
    void updateRetention_siNoExistePoliticaPrevia_creaUnaNueva() {
        when(pdcRepo.findById(pdcId)).thenReturn(Optional.of(linkedEntity()));
        mockUnlockedDocument();
        when(retentionRepo.findByPurposeDataCategoryId(pdcId)).thenReturn(Optional.empty());

        service.updateRetention(pdcId, retentionRequest());

        verify(retentionRepo).save(any(DataRetentionPolicies.class));
    }

    @Test
    void updateRetention_noSobrescribeLegalJustification_siVieneNuloEnElRequest() {
        when(pdcRepo.findById(pdcId)).thenReturn(Optional.of(linkedEntity()));
        mockUnlockedDocument();

        DataRetentionPolicies existing = new DataRetentionPolicies();
        existing.setPurposeDataCategoryId(pdcId);
        existing.setRetentionPeriod(3);
        existing.setRetentionUnit("YEARS");
        existing.setLegalJustification("Justificación original");
        when(retentionRepo.findByPurposeDataCategoryId(pdcId)).thenReturn(Optional.of(existing));

        DataRetentionPolicyRequest reqSinJustificacion = retentionRequest();
        reqSinJustificacion.setLegalJustification(null);

        service.updateRetention(pdcId, reqSinJustificacion);

        assertThat(existing.getLegalJustification()).isEqualTo("Justificación original");
    }

    // ── unlink() ─────────────────────────────────────────────────────────────────

    @Test
    void unlink_desvinculaYEliminaLaPoliticaDeRetencionAsociada() {
        when(pdcRepo.findById(pdcId)).thenReturn(Optional.of(linkedEntity()));
        mockUnlockedDocument();
        DataRetentionPolicies existing = new DataRetentionPolicies();
        when(retentionRepo.findByPurposeDataCategoryId(pdcId)).thenReturn(Optional.of(existing));

        service.unlink(pdcId);

        verify(retentionRepo).delete(existing);
        verify(pdcRepo).delete(any(PurposeDataCategories.class));
    }

    @Test
    void unlink_siNoHayPoliticaDeRetencionAsociada_noFalla() {
        when(pdcRepo.findById(pdcId)).thenReturn(Optional.of(linkedEntity()));
        mockUnlockedDocument();
        when(retentionRepo.findByPurposeDataCategoryId(pdcId)).thenReturn(Optional.empty());

        service.unlink(pdcId);

        verify(retentionRepo, never()).delete(any(DataRetentionPolicies.class));
        verify(pdcRepo).delete(any(PurposeDataCategories.class));
    }

    @Test
    void unlink_lanzaExcepcion_siLaFinalidadEstaBloqueada() {
        when(pdcRepo.findById(pdcId)).thenReturn(Optional.of(linkedEntity()));
        mockLockedDocument();

        assertThatThrownBy(() -> service.unlink(pdcId))
                .isInstanceOf(RetentionPolicyLockedException.class);
    }

    @Test
    void unlink_lanzaExcepcion_siElVinculoNoExiste() {
        when(pdcRepo.findById(pdcId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unlink(pdcId))
                .isInstanceOf(PurposeDataCategoryNotFoundException.class);
    }
}
