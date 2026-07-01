package com.leydata.backend.datacategory.application.service;

import com.leydata.backend.audit.application.service.AuditService;
import com.leydata.backend.datacategory.application.dto.DataCategoryRequest;
import com.leydata.backend.datacategory.application.dto.DataCategoryResponse;
import com.leydata.backend.datacategory.domain.exception.DataCategoryNotFoundException;
import com.leydata.backend.datacategory.infrastructure.persistence.DataCategoryRepository;
import com.leydata.backend.entity.DataCategories;
import com.leydata.backend.privacydoc.domain.exception.BusinessValidationException;
import com.leydata.backend.purposedatacategory.infrastructure.persistence.PurposeDataCategoryRepository;
import com.leydata.backend.shared.SecurityContextHelper;

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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataCategoryServiceTest {

    @Mock private DataCategoryRepository repo;
    @Mock private PurposeDataCategoryRepository pdcRepo;
    @Mock private AuditService auditService;
    @Mock private SecurityContextHelper securityContextHelper;

    @InjectMocks
    private DataCategoryService service;

    private final UUID categoryId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(securityContextHelper.getKeycloakId()).thenReturn("actor-keycloak-id");
        lenient().when(securityContextHelper.getActorRole()).thenReturn("DPO");
        lenient().when(repo.save(any(DataCategories.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private DataCategories category(boolean isSystem, boolean isActive) {
        return DataCategories.builder()
                .id(categoryId)
                .code("FINANCIERO")
                .name("Financiero")
                .description("Datos financieros")
                .isSensitive(false)
                .isSystem(isSystem)
                .isActive(isActive)
                .build();
    }

    private DataCategoryRequest request(String code, String name, Boolean isSensitive) {
        DataCategoryRequest req = new DataCategoryRequest();
        req.setCode(code);
        req.setName(name);
        req.setIsSensitive(isSensitive);
        return req;
    }

    // ── listAll() ────────────────────────────────────────────────────────────────

    @Test
    void listAll_devuelveSoloCategoriasActivas() {
        when(repo.findByIsActiveTrue()).thenReturn(List.of(category(false, true)));

        List<DataCategoryResponse> result = service.listAll();

        assertThat(result).hasSize(1);
    }

    // ── listSensitive() ──────────────────────────────────────────────────────────

    @Test
    void listSensitive_devuelveSoloCategoriasSensiblesYActivas() {
        when(repo.findByIsSensitiveAndIsActiveTrue(true)).thenReturn(List.of(category(false, true)));

        List<DataCategoryResponse> result = service.listSensitive();

        assertThat(result).hasSize(1);
    }

    // ── getById() ────────────────────────────────────────────────────────────────

    @Test
    void getById_devuelveLaCategoria() {
        when(repo.findById(categoryId)).thenReturn(Optional.of(category(false, true)));

        DataCategoryResponse response = service.getById(categoryId);

        assertThat(response.getCode()).isEqualTo("FINANCIERO");
    }

    @Test
    void getById_lanzaExcepcion_siNoExiste() {
        when(repo.findById(categoryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(categoryId))
                .isInstanceOf(DataCategoryNotFoundException.class);
    }

    // ── create() ─────────────────────────────────────────────────────────────────

    @Test
    void create_creaCategoriaConCodigoEnMayusculasNoSistemaYActiva() {
        when(repo.existsByCode("FINANCIERO")).thenReturn(false);

        DataCategoryResponse response = service.create(request("financiero", "Financiero", false));

        assertThat(response.getCode()).isEqualTo("FINANCIERO");
    }

    @Test
    void create_lanzaExcepcion_siElCodigoYaExiste() {
        when(repo.existsByCode("FINANCIERO")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("financiero", "Financiero", false)))
                .isInstanceOf(BusinessValidationException.class);
    }

    // ── update() ─────────────────────────────────────────────────────────────────

    @Test
    void update_actualizaLosCamposEnviados() {
        when(repo.findById(categoryId)).thenReturn(Optional.of(category(false, true)));
        when(repo.existsByCode("LABORAL")).thenReturn(false);

        DataCategoryResponse response = service.update(categoryId, request("laboral", "Datos laborales", true));

        assertThat(response.getCode()).isEqualTo("LABORAL");
        assertThat(response.getName()).isEqualTo("Datos laborales");
        assertThat(response.getIsSensitive()).isTrue();
    }

    @Test
    void update_lanzaExcepcion_siLaCategoriaEsDelSistema() {
        when(repo.findById(categoryId)).thenReturn(Optional.of(category(true, true)));

        assertThatThrownBy(() -> service.update(categoryId, request("financiero", "Financiero", false)))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void update_lanzaExcepcion_siElNuevoCodigoYaExisteEnOtraCategoria() {
        when(repo.findById(categoryId)).thenReturn(Optional.of(category(false, true)));
        when(repo.existsByCode("LABORAL")).thenReturn(true);

        assertThatThrownBy(() -> service.update(categoryId, request("laboral", "Datos laborales", null)))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void update_noValidaDuplicado_siElCodigoEnviadoEsElMismoQueYaTiene() {
        when(repo.findById(categoryId)).thenReturn(Optional.of(category(false, true)));

        service.update(categoryId, request("financiero", "Nuevo nombre", null));

        verify(repo, never()).existsByCode(any());
    }

    @Test
    void update_camposNulos_noSobrescribenLosExistentes() {
        when(repo.findById(categoryId)).thenReturn(Optional.of(category(false, true)));

        DataCategoryRequest req = new DataCategoryRequest();
        // code, name, description, isSensitive todos nulos salvo los obligatorios en el DTO real,
        // pero el service solo aplica los que no son null.
        DataCategoryResponse response = service.update(categoryId, req);

        assertThat(response.getCode()).isEqualTo("FINANCIERO");
        assertThat(response.getName()).isEqualTo("Financiero");
    }

    // ── deactivate() ─────────────────────────────────────────────────────────────

    @Test
    void deactivate_desactivaCategoriaSinVinculos() {
        when(repo.findById(categoryId)).thenReturn(Optional.of(category(false, true)));
        when(pdcRepo.existsByDataCategoryId(categoryId)).thenReturn(false);

        DataCategoryResponse response = service.deactivate(categoryId);

        assertThat(response.getIsActive()).isFalse();
    }

    @Test
    void deactivate_lanzaExcepcion_siLaCategoriaEsDelSistema() {
        when(repo.findById(categoryId)).thenReturn(Optional.of(category(true, true)));

        assertThatThrownBy(() -> service.deactivate(categoryId))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void deactivate_lanzaExcepcion_siEstaVinculadaAAlgunaFinalidad() {
        when(repo.findById(categoryId)).thenReturn(Optional.of(category(false, true)));
        when(pdcRepo.existsByDataCategoryId(categoryId)).thenReturn(true);

        assertThatThrownBy(() -> service.deactivate(categoryId))
                .isInstanceOf(BusinessValidationException.class);
    }

    @Test
    void deactivate_lanzaExcepcion_siNoExiste() {
        when(repo.findById(categoryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivate(categoryId))
                .isInstanceOf(DataCategoryNotFoundException.class);
    }
}
