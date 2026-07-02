package com.leydata.backend.orgdomain.application.service;

import com.leydata.backend.audit.application.dto.AuditContext;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DomainService {

    private final DomainsRepository domainsRepository;
    private final UsersRepository usersRepository;
    private final UserDomainRepository userDomainRepository;
    private final AuditService auditService;
    private final SecurityContextHelper securityContextHelper;
    private final KeycloakAdminService keycloakAdminService;

    @Transactional
    public DomainResponse createDomain(CreateDomainRequest request) {
        securityContextHelper.requireAdmin();

        if (domainsRepository.findByCode(request.getCode()).isPresent()) {
            throw new IllegalArgumentException("Ya existe un dominio con ese codigo");
        }

        Users jefe = null;
        if (request.getJefeId() != null) {
            jefe = usersRepository.findById(request.getJefeId())
                    .orElseThrow(() -> new IllegalArgumentException("El usuario jefe no existe"));
            List<String> jefeRoles = keycloakAdminService.getUserRoles(jefe.getKeycloakId());
            if (!jefeRoles.contains("JEFE_DOMINIO")) {
                throw new IllegalArgumentException("El usuario no tiene el rol JEFE_DOMINIO");
            }
        }

        Domains domain = new Domains();
        domain.setCode(request.getCode());
        domain.setName(request.getName());
        domain.setDescription(request.getDescription());
        domain.setActive(true);
        domain.setCreatedAt(LocalDateTime.now());
        Domains savedDomain = domainsRepository.save(domain);

        if (jefe != null && jefe.getKeycloakId() != null) {
            userDomainRepository.save(UserDomain.builder()
                    .keycloakId(jefe.getKeycloakId())
                    .domain(savedDomain)
                    .build());
        }

        auditService.log(AuditContext.builder()
                .tableName("domains").recordId(savedDomain.getId()).action("CREAR_DOMINIO")
                .oldData(null)
                .newData(Map.of("id", String.valueOf(savedDomain.getId()),
                        "code", savedDomain.getCode() != null ? savedDomain.getCode() : "",
                        "name", savedDomain.getName(), "active", String.valueOf(savedDomain.getActive())))
                .actorId(securityContextHelper.getKeycloakId()).actorRole(securityContextHelper.getActorRole()).build());

        return DomainResponse.from(savedDomain);
    }

    @Transactional(readOnly = true)
    public List<DomainResponse> getAllDomains() {
        return domainsRepository.findAll().stream().map(DomainResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<DomainResponse> getActiveDomains() {
        return domainsRepository.findByActiveTrue().stream().map(DomainResponse::from).toList();
    }

    @Transactional
    public DomainResponse deactivateDomain(UUID domainId) {
        securityContextHelper.requireAdmin();
        Domains domain = domainsRepository.findById(domainId)
                .orElseThrow(() -> new DomainNotFoundException("Dominio no encontrado: " + domainId));
        if (!Boolean.TRUE.equals(domain.getActive()))
            throw new IllegalStateException("El dominio ya esta desactivado");
        Map<String, Object> oldData = Map.of("active", true, "name", domain.getName());
        domain.setActive(false);
        Domains saved = domainsRepository.save(domain);
        auditService.log(AuditContext.builder().tableName("domains").recordId(saved.getId())
                .action("DESACTIVAR_DOMINIO").oldData(oldData)
                .newData(Map.of("active", false, "name", saved.getName()))
                .actorId(securityContextHelper.getKeycloakId()).actorRole(securityContextHelper.getActorRole()).build());
        return DomainResponse.from(saved);
    }

    @Transactional
    public DomainResponse reactivateDomain(UUID domainId) {
        securityContextHelper.requireAdmin();
        Domains domain = domainsRepository.findById(domainId)
                .orElseThrow(() -> new DomainNotFoundException("Dominio no encontrado: " + domainId));
        if (Boolean.TRUE.equals(domain.getActive()))
            throw new IllegalStateException("El dominio ya esta activo");
        Map<String, Object> oldData = Map.of("active", false, "name", domain.getName());
        domain.setActive(true);
        Domains saved = domainsRepository.save(domain);
        auditService.log(AuditContext.builder().tableName("domains").recordId(saved.getId())
                .action("REACTIVAR_DOMINIO").oldData(oldData)
                .newData(Map.of("active", true, "name", saved.getName()))
                .actorId(securityContextHelper.getKeycloakId()).actorRole(securityContextHelper.getActorRole()).build());
        return DomainResponse.from(saved);
    }
}
