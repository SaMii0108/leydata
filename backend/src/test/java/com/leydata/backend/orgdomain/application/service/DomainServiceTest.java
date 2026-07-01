package com.leydata.backend.orgdomain.application.service;

import com.leydata.backend.audit.application.service.AuditService;
import com.leydata.backend.entity.Domains;
import com.leydata.backend.entity.Users;
import com.leydata.backend.orgdomain.application.dto.CreateDomainRequest;
import com.leydata.backend.orgdomain.application.dto.DomainResponse;
import com.leydata.backend.orgdomain.domain.exception.DomainNotFoundException;
import com.leydata.backend.orgdomain.infrastructure.persistence.DomainsRepository;
import com.leydata.backend.security.KeycloakAdminService;
import com.leydata.backend.shared.SecurityContextHelper;
import com.leydata.backend.user.infrastructure.persistence.UsersRepository;
import com.leydata.backend.userdomain.domain.UserDomain;
import com.leydata.backend.userdomain.infrastructure.persistence.UserDomainRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class DomainServiceTest {

    @Mock private DomainsRepository domainsRepository;
    @Mock private UsersRepository usersRepository;
    @Mock private UserDomainRepository userDomainRepository;
    @Mock private AuditService auditService;
    @Mock private SecurityContextHelper securityContextHelper;
    @Mock private KeycloakAdminService keycloakAdminService;

    @InjectMocks
    private DomainService service;

    private final UUID domainId = UUID.randomUUID();
    private final UUID jefeId = UUID.randomUUID();
    private final String jefeKeycloakId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        lenient().doNothing().when(securityContextHelper).requireAdmin();
        lenient().when(domainsRepository.save(any(Domains.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private CreateDomainRequest request(UUID jefeId) {
        CreateDomainRequest req = new CreateDomainRequest();
        req.setCode("mkt");
        req.setName("Marketing Digital");
        req.setDescription("Área de marketing digital");
        req.setJefeId(jefeId);
        return req;
    }

    private Users jefeUser() {
        Users u = new Users();
        u.setId(jefeId);
        u.setKeycloakId(jefeKeycloakId);
        u.setEmail("jefe@test.cl");
        return u;
    }

    private Domains domain(boolean active) {
        Domains d = new Domains();
        d.setId(domainId);
        d.setCode("mkt");
        d.setName("Marketing Digital");
        d.setActive(active);
        return d;
    }

    // ── createDomain() ──────────────────────────────────────────────────────────

    @Test
    void createDomain_creaDominioSinJefeAsignado() {
        when(domainsRepository.findByCode("mkt")).thenReturn(Optional.empty());

        DomainResponse response = service.createDomain(request(null));

        assertThat(response.getCode()).isEqualTo("mkt");
        assertThat(response.getActive()).isTrue();
        verify(userDomainRepository, never()).save(any(UserDomain.class));
    }

    @Test
    void createDomain_lanzaExcepcion_siCodigoYaExiste() {
        when(domainsRepository.findByCode("mkt")).thenReturn(Optional.of(domain(true)));

        assertThatThrownBy(() -> service.createDomain(request(null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createDomain_lanzaExcepcion_siJefeIdNoExiste() {
        when(domainsRepository.findByCode("mkt")).thenReturn(Optional.empty());
        when(usersRepository.findById(jefeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createDomain(request(jefeId)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createDomain_lanzaExcepcion_siUsuarioNoTieneRolJefeDominio() {
        when(domainsRepository.findByCode("mkt")).thenReturn(Optional.empty());
        when(usersRepository.findById(jefeId)).thenReturn(Optional.of(jefeUser()));
        when(keycloakAdminService.getUserRoles(jefeKeycloakId)).thenReturn(List.of("USER"));

        assertThatThrownBy(() -> service.createDomain(request(jefeId)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createDomain_conJefeValido_creaAsignacionEnUserDomain() {
        when(domainsRepository.findByCode("mkt")).thenReturn(Optional.empty());
        when(usersRepository.findById(jefeId)).thenReturn(Optional.of(jefeUser()));
        when(keycloakAdminService.getUserRoles(jefeKeycloakId)).thenReturn(List.of("JEFE_DOMINIO"));

        service.createDomain(request(jefeId));

        ArgumentCaptor<UserDomain> captor = ArgumentCaptor.forClass(UserDomain.class);
        verify(userDomainRepository).save(captor.capture());
        assertThat(captor.getValue().getKeycloakId()).isEqualTo(jefeKeycloakId);
    }

    // ── getAllDomains() ──────────────────────────────────────────────────────────

    @Test
    void getAllDomains_devuelveTodosLosDominiosMapeados() {
        when(domainsRepository.findAll()).thenReturn(List.of(domain(true), domain(false)));

        List<DomainResponse> result = service.getAllDomains();

        assertThat(result).hasSize(2);
    }

    // ── deactivateDomain() ───────────────────────────────────────────────────────

    @Test
    void deactivateDomain_desactivaDominioActivo() {
        when(domainsRepository.findById(domainId)).thenReturn(Optional.of(domain(true)));

        DomainResponse response = service.deactivateDomain(domainId);

        assertThat(response.getActive()).isFalse();
    }

    @Test
    void deactivateDomain_lanzaExcepcion_siNoExiste() {
        when(domainsRepository.findById(domainId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivateDomain(domainId))
                .isInstanceOf(DomainNotFoundException.class);
    }

    @Test
    void deactivateDomain_lanzaExcepcion_siYaEstaInactivo() {
        when(domainsRepository.findById(domainId)).thenReturn(Optional.of(domain(false)));

        assertThatThrownBy(() -> service.deactivateDomain(domainId))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── reactivateDomain() ───────────────────────────────────────────────────────

    @Test
    void reactivateDomain_reactivaDominioInactivo() {
        when(domainsRepository.findById(domainId)).thenReturn(Optional.of(domain(false)));

        DomainResponse response = service.reactivateDomain(domainId);

        assertThat(response.getActive()).isTrue();
    }

    @Test
    void reactivateDomain_lanzaExcepcion_siNoExiste() {
        when(domainsRepository.findById(domainId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reactivateDomain(domainId))
                .isInstanceOf(DomainNotFoundException.class);
    }

    @Test
    void reactivateDomain_lanzaExcepcion_siYaEstaActivo() {
        when(domainsRepository.findById(domainId)).thenReturn(Optional.of(domain(true)));

        assertThatThrownBy(() -> service.reactivateDomain(domainId))
                .isInstanceOf(IllegalStateException.class);
    }
}
