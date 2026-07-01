package com.leydata.backend.user.application.service;

import com.leydata.backend.audit.application.service.AuditService;
import com.leydata.backend.entity.Domains;
import com.leydata.backend.orgdomain.infrastructure.persistence.DomainsRepository;
import com.leydata.backend.security.KeycloakAdminService;
import com.leydata.backend.shared.SecurityContextHelper;
import com.leydata.backend.user.application.dto.CreateUserRequest;
import com.leydata.backend.user.application.dto.UpdateUserByAdminRequest;
import com.leydata.backend.user.application.dto.UserResponse;
import com.leydata.backend.userdomain.domain.UserDomain;
import com.leydata.backend.userdomain.infrastructure.persistence.UserDomainRepository;
import com.leydata.backend.userstatus.domain.UserStatus;
import com.leydata.backend.userstatus.infrastructure.persistence.UserStatusRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Map;
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
class UserServiceTest {

    @Mock private DomainsRepository domainsRepository;
    @Mock private UserDomainRepository userDomainRepository;
    @Mock private UserStatusRepository userStatusRepository;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private AuditService auditService;
    @Mock private KeycloakAdminService keycloakAdminService;
    @Mock private SecurityContextHelper securityContextHelper;

    @InjectMocks
    private UserService service;

    private final String actorKeycloakId = UUID.randomUUID().toString();
    private final String targetKeycloakId = UUID.randomUUID().toString();
    private final UUID domainId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().doNothing().when(securityContextHelper).requireAdmin();
        lenient().when(securityContextHelper.getKeycloakId()).thenReturn(actorKeycloakId);
        lenient().when(securityContextHelper.getActorRole()).thenReturn("ADMIN");
        lenient().when(userDomainRepository.findByKeycloakId(any())).thenReturn(List.of());
        lenient().when(userStatusRepository.findById(any())).thenReturn(Optional.empty());
    }

    private Domains activeDomain() {
        Domains d = new Domains();
        d.setId(domainId);
        d.setName("Marketing");
        d.setActive(true);
        return d;
    }

    private CreateUserRequest createRequest(String roleCode, List<UUID> domainIds) {
        CreateUserRequest req = new CreateUserRequest();
        req.setEmail("nuevo@empresa.cl");
        req.setName("Nuevo Usuario");
        req.setPassword("Temporal123!");
        req.setRoleCode(roleCode);
        req.setDomainIds(domainIds);
        return req;
    }

    private Map<String, Object> kcUser(String email, String name, boolean enabled) {
        return Map.of("email", email, "name", name, "enabled", enabled);
    }

    // ── createUser() ─────────────────────────────────────────────────────────────

    @Test
    void createUser_creaUsuarioJefeDominioConUnDominioValidoAsignado() {
        when(keycloakAdminService.createUser("nuevo@empresa.cl", "Nuevo Usuario", "JEFE_DOMINIO", "Temporal123!"))
                .thenReturn(targetKeycloakId);
        when(domainsRepository.findById(domainId)).thenReturn(Optional.of(activeDomain()));

        UserResponse response = service.createUser(createRequest("JEFE_DOMINIO", List.of(domainId)));

        assertThat(response.getKeycloakId()).isEqualTo(targetKeycloakId);
        verify(userDomainRepository).save(any(UserDomain.class));
    }

    @Test
    void createUser_creaUsuarioSinDominios() {
        when(keycloakAdminService.createUser("nuevo@empresa.cl", "Nuevo Usuario", "USER", "Temporal123!"))
                .thenReturn(targetKeycloakId);

        UserResponse response = service.createUser(createRequest("USER", null));

        assertThat(response.getKeycloakId()).isEqualTo(targetKeycloakId);
        verify(userDomainRepository, never()).save(any(UserDomain.class));
    }

    @Test
    void createUser_lanzaExcepcion_siLaContrasenaEsNulaOEnBlanco() {
        CreateUserRequest req = createRequest("USER", null);
        req.setPassword("  ");

        assertThatThrownBy(() -> service.createUser(req))
                .isInstanceOf(IllegalArgumentException.class);
        verify(keycloakAdminService, never()).createUser(any(), any(), any(), any());
    }

    @Test
    void createUser_lanzaExcepcion_siAsignaDominiosAUnRolQueNoEsJefeDominio() {
        assertThatThrownBy(() -> service.createUser(createRequest("USER", List.of(domainId))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createUser_lanzaExcepcion_siAsignaMasDeUnDominio() {
        assertThatThrownBy(() -> service.createUser(
                createRequest("JEFE_DOMINIO", List.of(domainId, UUID.randomUUID()))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createUser_siFallaLaAsignacionDeDominio_compensaEliminandoElUsuarioDeKeycloak() {
        when(keycloakAdminService.createUser(any(), any(), any(), any())).thenReturn(targetKeycloakId);
        when(domainsRepository.findById(domainId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createUser(createRequest("JEFE_DOMINIO", List.of(domainId))))
                .isInstanceOf(RuntimeException.class);

        verify(keycloakAdminService).deleteUser(targetKeycloakId);
    }

    // ── updateUserByAdmin() ──────────────────────────────────────────────────────

    @Test
    void updateUserByAdmin_actualizaNombreYEmail() {
        when(keycloakAdminService.getUser(targetKeycloakId))
                .thenReturn(kcUser("viejo@empresa.cl", "Nombre Viejo", true))
                .thenReturn(kcUser("nuevo@empresa.cl", "Nombre Nuevo", true));
        when(keycloakAdminService.getUserRoles(targetKeycloakId)).thenReturn(List.of("USER"));

        UpdateUserByAdminRequest req = new UpdateUserByAdminRequest();
        req.setName("Nombre Nuevo");
        req.setEmail("nuevo@empresa.cl");

        UserResponse response = service.updateUserByAdmin(targetKeycloakId, req);

        verify(keycloakAdminService).updateUserProfile(targetKeycloakId, "nuevo@empresa.cl", "Nombre Nuevo");
        assertThat(response.getEmail()).isEqualTo("nuevo@empresa.cl");
    }

    @Test
    void updateUserByAdmin_reseteaPassword_siVieneEnElRequest() {
        when(keycloakAdminService.getUser(targetKeycloakId)).thenReturn(kcUser("a@a.cl", "A", true));
        when(keycloakAdminService.getUserRoles(targetKeycloakId)).thenReturn(List.of("USER"));

        UpdateUserByAdminRequest req = new UpdateUserByAdminRequest();
        req.setPassword("NuevaPass123!");

        service.updateUserByAdmin(targetKeycloakId, req);

        verify(keycloakAdminService).resetPassword(targetKeycloakId, "NuevaPass123!", true);
    }

    @Test
    void updateUserByAdmin_alQuitarRolJefeDominio_limpiaLosDominiosAsignados() {
        when(keycloakAdminService.getUser(targetKeycloakId)).thenReturn(kcUser("a@a.cl", "A", true));
        when(keycloakAdminService.getUserRoles(targetKeycloakId)).thenReturn(List.of("JEFE_DOMINIO"));

        UpdateUserByAdminRequest req = new UpdateUserByAdminRequest();
        req.setRoleCodes(List.of("USER"));

        service.updateUserByAdmin(targetKeycloakId, req);

        verify(userDomainRepository).deleteByKeycloakId(targetKeycloakId);
    }

    @Test
    void updateUserByAdmin_lanzaExcepcion_siElUsuarioEstaBloqueado() {
        when(userStatusRepository.findById(targetKeycloakId))
                .thenReturn(Optional.of(UserStatus.builder().keycloakId(targetKeycloakId).blocked(true).build()));

        assertThatThrownBy(() -> service.updateUserByAdmin(targetKeycloakId, new UpdateUserByAdminRequest()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void updateUserByAdmin_domainIdsVacio_limpiaLosDominiosAsignados() {
        when(keycloakAdminService.getUser(targetKeycloakId)).thenReturn(kcUser("a@a.cl", "A", true));
        when(keycloakAdminService.getUserRoles(targetKeycloakId)).thenReturn(List.of("JEFE_DOMINIO"));

        UpdateUserByAdminRequest req = new UpdateUserByAdminRequest();
        req.setDomainIds(List.of());

        service.updateUserByAdmin(targetKeycloakId, req);

        verify(userDomainRepository).deleteByKeycloakId(targetKeycloakId);
    }

    @Test
    void updateUserByAdmin_lanzaExcepcion_siAsignaDominioAUnUsuarioSinRolJefeDominio() {
        when(keycloakAdminService.getUser(targetKeycloakId)).thenReturn(kcUser("a@a.cl", "A", true));
        when(keycloakAdminService.getUserRoles(targetKeycloakId)).thenReturn(List.of("USER"));

        UpdateUserByAdminRequest req = new UpdateUserByAdminRequest();
        req.setDomainIds(List.of(domainId));

        assertThatThrownBy(() -> service.updateUserByAdmin(targetKeycloakId, req))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── deactivateUser() ─────────────────────────────────────────────────────────

    @Test
    void deactivateUser_desactivaCorrectamente() {
        when(keycloakAdminService.getUserRoles(targetKeycloakId)).thenReturn(List.of("USER"));
        when(keycloakAdminService.getUser(targetKeycloakId)).thenReturn(kcUser("a@a.cl", "A", true));

        UserResponse response = service.deactivateUser(targetKeycloakId);

        assertThat(response.getActive()).isFalse();
        verify(keycloakAdminService).disableUser(targetKeycloakId);
    }

    @Test
    void deactivateUser_lanzaExcepcion_siIntentaDesactivarseASiMismo() {
        assertThatThrownBy(() -> service.deactivateUser(actorKeycloakId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deactivateUser_lanzaExcepcion_siElObjetivoEsAdmin() {
        when(keycloakAdminService.getUserRoles(targetKeycloakId)).thenReturn(List.of("ADMIN"));

        assertThatThrownBy(() -> service.deactivateUser(targetKeycloakId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deactivateUser_lanzaExcepcion_siEstaBloqueado() {
        when(keycloakAdminService.getUserRoles(targetKeycloakId)).thenReturn(List.of("USER"));
        when(userStatusRepository.findById(targetKeycloakId))
                .thenReturn(Optional.of(UserStatus.builder().keycloakId(targetKeycloakId).blocked(true).build()));

        assertThatThrownBy(() -> service.deactivateUser(targetKeycloakId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deactivateUser_lanzaExcepcion_siYaEstaDesactivado() {
        when(keycloakAdminService.getUserRoles(targetKeycloakId)).thenReturn(List.of("USER"));
        when(keycloakAdminService.getUser(targetKeycloakId)).thenReturn(kcUser("a@a.cl", "A", false));

        assertThatThrownBy(() -> service.deactivateUser(targetKeycloakId))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── reactivateUser() ─────────────────────────────────────────────────────────

    @Test
    void reactivateUser_reactivaCorrectamente() {
        when(keycloakAdminService.getUser(targetKeycloakId)).thenReturn(kcUser("a@a.cl", "A", false));
        when(keycloakAdminService.getUserRoles(targetKeycloakId)).thenReturn(List.of("USER"));

        UserResponse response = service.reactivateUser(targetKeycloakId);

        assertThat(response.getActive()).isTrue();
        verify(keycloakAdminService).enableUser(targetKeycloakId);
    }

    @Test
    void reactivateUser_lanzaExcepcion_siEstaBloqueado() {
        when(userStatusRepository.findById(targetKeycloakId))
                .thenReturn(Optional.of(UserStatus.builder().keycloakId(targetKeycloakId).blocked(true).build()));

        assertThatThrownBy(() -> service.reactivateUser(targetKeycloakId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reactivateUser_lanzaExcepcion_siYaEstaActivo() {
        when(keycloakAdminService.getUser(targetKeycloakId)).thenReturn(kcUser("a@a.cl", "A", true));

        assertThatThrownBy(() -> service.reactivateUser(targetKeycloakId))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── blockUser() ──────────────────────────────────────────────────────────────

    @Test
    void blockUser_bloqueaCorrectamente_deshabilitaGuardaEstadoYEscribeEnRedis() {
        when(keycloakAdminService.getUserRoles(targetKeycloakId)).thenReturn(List.of("USER"));
        when(keycloakAdminService.getUser(targetKeycloakId)).thenReturn(kcUser("a@a.cl", "A", true));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        UserResponse response = service.blockUser(targetKeycloakId);

        assertThat(response.getActive()).isFalse();
        verify(keycloakAdminService).disableUser(targetKeycloakId);
        verify(userStatusRepository).save(any(UserStatus.class));
        verify(valueOperations).set("user:" + targetKeycloakId + ":blocked", "true");
    }

    @Test
    void blockUser_lanzaExcepcion_siIntentaBloquearseASiMismo() {
        assertThatThrownBy(() -> service.blockUser(actorKeycloakId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void blockUser_lanzaExcepcion_siElObjetivoEsAdmin() {
        when(keycloakAdminService.getUserRoles(targetKeycloakId)).thenReturn(List.of("ADMIN"));

        assertThatThrownBy(() -> service.blockUser(targetKeycloakId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void blockUser_lanzaExcepcion_siYaEstaBloqueado() {
        when(keycloakAdminService.getUserRoles(targetKeycloakId)).thenReturn(List.of("USER"));
        when(userStatusRepository.findById(targetKeycloakId))
                .thenReturn(Optional.of(UserStatus.builder().keycloakId(targetKeycloakId).blocked(true).build()));

        assertThatThrownBy(() -> service.blockUser(targetKeycloakId))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── getAllUsers() ────────────────────────────────────────────────────────────

    @Test
    void getAllUsers_filtraPorStatusBlocked() {
        when(keycloakAdminService.listUsers(null)).thenReturn(List.of(
                Map.of("keycloakId", "u1", "email", "u1@a.cl", "name", "U1", "enabled", true, "roleCodes", List.of("USER")),
                Map.of("keycloakId", "u2", "email", "u2@a.cl", "name", "U2", "enabled", true, "roleCodes", List.of("USER"))));
        when(userStatusRepository.findById("u1"))
                .thenReturn(Optional.of(UserStatus.builder().keycloakId("u1").blocked(true).build()));
        when(userStatusRepository.findById("u2")).thenReturn(Optional.empty());

        List<UserResponse> result = service.getAllUsers(null, "blocked", null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getKeycloakId()).isEqualTo("u1");
    }

    @Test
    void getAllUsers_filtraPorRole() {
        when(keycloakAdminService.listUsers(null)).thenReturn(List.of(
                Map.of("keycloakId", "u1", "email", "u1@a.cl", "name", "U1", "enabled", true, "roleCodes", List.of("DPO")),
                Map.of("keycloakId", "u2", "email", "u2@a.cl", "name", "U2", "enabled", true, "roleCodes", List.of("USER"))));

        List<UserResponse> result = service.getAllUsers(null, null, "DPO");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getKeycloakId()).isEqualTo("u1");
    }

    // ── getUserByKeycloakId() ────────────────────────────────────────────────────

    @Test
    void getUserByKeycloakId_devuelveElUsuarioEnsamblado() {
        when(keycloakAdminService.getUser(targetKeycloakId)).thenReturn(kcUser("a@a.cl", "A", true));
        when(keycloakAdminService.getUserRoles(targetKeycloakId)).thenReturn(List.of("DPO"));

        UserResponse response = service.getUserByKeycloakId(targetKeycloakId);

        assertThat(response.getRoles()).containsExactly("DPO");
        assertThat(response.getEmail()).isEqualTo("a@a.cl");
    }
}
