package com.leydata.backend.orgdomain.application.service;

import com.leydata.backend.audit.application.dto.AuditContext;
import com.leydata.backend.audit.application.service.AuditService;
import com.leydata.backend.entity.Domains;
import com.leydata.backend.entity.UserDomains;
import com.leydata.backend.entity.Users;
import com.leydata.backend.orgdomain.application.dto.CreateDomainRequest;
import com.leydata.backend.orgdomain.application.dto.DomainResponse;
import com.leydata.backend.orgdomain.domain.exception.DomainNotFoundException;
import com.leydata.backend.orgdomain.infrastructure.persistence.DomainsRepository;
import com.leydata.backend.orgdomain.infrastructure.persistence.UserDomainsRepository;
import com.leydata.backend.shared.SecurityContextHelper;
import com.leydata.backend.user.infrastructure.persistence.UsersRepository;
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
    private final UserDomainsRepository userDomainsRepository;
    private final AuditService auditService;
    private final SecurityContextHelper securityContextHelper;

    // CREAR DOMINIO (solo ADMIN)
    @Transactional
    public DomainResponse createDomain(CreateDomainRequest request) {
        Users admin = securityContextHelper.getAuthenticatedAdmin();

        if (domainsRepository.findByCode(request.getCode()).isPresent()) {
            throw new IllegalArgumentException("Ya existe un dominio con ese código");
        }

        Users jefe = null;
        if (request.getJefeId() != null) {
            jefe = usersRepository.findById(request.getJefeId())
                    .orElseThrow(() -> new IllegalArgumentException("El usuario jefe no existe"));

            boolean hasJefeRole = jefe.getUserRoles().stream()
                    .anyMatch(ur -> "JEFE_DOMINIO".equals(ur.getRole().getCode()));
            if (!hasJefeRole) {
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

        if (jefe != null) {
            UserDomains userDomain = new UserDomains();
            UserDomains.UserDomainsId udId = new UserDomains.UserDomainsId();
            udId.setUserId(jefe.getId());
            udId.setDomainId(savedDomain.getId());
            userDomain.setId(udId);
            userDomain.setUser(jefe);
            userDomain.setDomain(savedDomain);
            userDomainsRepository.save(userDomain);
        }

        auditService.log(AuditContext.builder()
                .tableName("domains")
                .recordId(savedDomain.getId())
                .action("CREAR_DOMINIO")
                .oldData(null)
                .newData(Map.of(
                        "id", String.valueOf(savedDomain.getId()),
                        "code", savedDomain.getCode() != null ? savedDomain.getCode() : "",
                        "name", savedDomain.getName(),
                        "active", String.valueOf(savedDomain.getActive())))
                .actorId(admin.getId())
                .actorRole(securityContextHelper.getActorRole())
                .build());

        return DomainResponse.from(savedDomain);
    }

    // LISTAR TODOS LOS DOMINIOS (solo ADMIN, incluye inactivos)
    @Transactional(readOnly = true)
    public List<DomainResponse> getAllDomains() {
        return domainsRepository.findAll().stream()
                .map(DomainResponse::from)
                .toList();
    }

    // DESACTIVAR DOMINIO (reversible)
    @Transactional
    public DomainResponse deactivateDomain(UUID domainId) {
        Users admin = securityContextHelper.getAuthenticatedAdmin();

        Domains domain = domainsRepository.findById(domainId)
                .orElseThrow(() -> new DomainNotFoundException("Dominio no encontrado: " + domainId));

        if (!Boolean.TRUE.equals(domain.getActive())) {
            throw new IllegalStateException("El dominio ya está desactivado");
        }

        Map<String, Object> oldData = Map.of("active", true, "name", domain.getName());

        domain.setActive(false);
        Domains saved = domainsRepository.save(domain);

        auditService.log(AuditContext.builder()
                .tableName("domains")
                .recordId(saved.getId())
                .action("DESACTIVAR_DOMINIO")
                .oldData(oldData)
                .newData(Map.of("active", false, "name", saved.getName()))
                .actorId(admin.getId())
                .actorRole(securityContextHelper.getActorRole())
                .build());

        return DomainResponse.from(saved);
    }

    // REACTIVAR DOMINIO
    @Transactional
    public DomainResponse reactivateDomain(UUID domainId) {
        Users admin = securityContextHelper.getAuthenticatedAdmin();

        Domains domain = domainsRepository.findById(domainId)
                .orElseThrow(() -> new DomainNotFoundException("Dominio no encontrado: " + domainId));

        if (Boolean.TRUE.equals(domain.getActive())) {
            throw new IllegalStateException("El dominio ya está activo");
        }

        Map<String, Object> oldData = Map.of("active", false, "name", domain.getName());

        domain.setActive(true);
        Domains saved = domainsRepository.save(domain);

        auditService.log(AuditContext.builder()
                .tableName("domains")
                .recordId(saved.getId())
                .action("REACTIVAR_DOMINIO")
                .oldData(oldData)
                .newData(Map.of("active", true, "name", saved.getName()))
                .actorId(admin.getId())
                .actorRole(securityContextHelper.getActorRole())
                .build());

        return DomainResponse.from(saved);
    }
}
