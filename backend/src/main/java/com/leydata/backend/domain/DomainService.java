package com.leydata.backend.domain;

import com.leydata.backend.audit.AuditContext;
import com.leydata.backend.audit.AuditService;
import com.leydata.backend.entity.Domains;
import com.leydata.backend.entity.UserDomains;
import com.leydata.backend.entity.Users;
import com.leydata.backend.user.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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

    // CREAR DOMINIO (solo ADMIN)
    @Transactional
    public Domains createDomain(CreateDomainRequest request) {
        Users admin = getAuthenticatedAdmin();

        if (domainsRepository.findByCode(request.getCode()).isPresent()) {
            throw new IllegalArgumentException("Ya existe un dominio con ese código");
        }

        // Validar el jefe de dominio si se proporcionó
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

        // Vincular el jefe al dominio recién creado
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

        // Registrar la creación del dominio: quién, cuándo, qué datos
        auditService.tryLog(AuditContext.builder()
                .tableName("domains")
                .recordId(savedDomain.getId())
                .action("CREAR_DOMINIO")
                .oldData(null)
                .newData(Map.of(
                        "id", savedDomain.getId(),
                        "code", savedDomain.getCode(),
                        "name", savedDomain.getName(),
                        "active", savedDomain.getActive()))
                .actorId(admin.getId())
                .actorRole(getActorRole())
                .build());

        return savedDomain;
    }

    // LISTAR TODOS LOS DOMINIOS (solo ADMIN, incluye inactivos)
    @Transactional(readOnly = true)
    public List<Domains> getAllDomains() {
        return domainsRepository.findAll();
    }

    // DESACTIVAR DOMINIO (reversible)
    @Transactional
    public Domains deactivateDomain(UUID domainId) {
        Users admin = getAuthenticatedAdmin();

        Domains domain = domainsRepository.findById(domainId)
                .orElseThrow(() -> new IllegalArgumentException("Dominio no encontrado: " + domainId));

        if (!Boolean.TRUE.equals(domain.getActive())) {
            throw new IllegalStateException("El dominio ya está desactivado");
        }

        // Snapshot del estado anterior
        Map<String, Object> oldData = Map.of("active", true, "name", domain.getName());

        domain.setActive(false);
        Domains saved = domainsRepository.save(domain);

        auditService.tryLog(AuditContext.builder()
                .tableName("domains")
                .recordId(saved.getId())
                .action("DESACTIVAR_DOMINIO")
                .oldData(oldData)
                .newData(Map.of("active", false, "name", saved.getName()))
                .actorId(admin.getId())
                .actorRole(getActorRole())
                .build());

        return saved;
    }

    // REACTIVAR DOMINIO
    @Transactional
    public Domains reactivateDomain(UUID domainId) {
        Users admin = getAuthenticatedAdmin();

        Domains domain = domainsRepository.findById(domainId)
                .orElseThrow(() -> new IllegalArgumentException("Dominio no encontrado: " + domainId));

        if (Boolean.TRUE.equals(domain.getActive())) {
            throw new IllegalStateException("El dominio ya está activo");
        }

        Map<String, Object> oldData = Map.of("active", false, "name", domain.getName());

        domain.setActive(true);
        Domains saved = domainsRepository.save(domain);

        auditService.tryLog(AuditContext.builder()
                .tableName("domains")
                .recordId(saved.getId())
                .action("REACTIVAR_DOMINIO")
                .oldData(oldData)
                .newData(Map.of("active", true, "name", saved.getName()))
                .actorId(admin.getId())
                .actorRole(getActorRole())
                .build());

        return saved;
    }

    // HELPERS DE AUTENTICACIÓN

    // Verifica que el usuario autenticado tiene rol ADMIN (desde el JWT de
    // Keycloak)
    // y retorna su entidad local para usarla en auditoría
    private Users getAuthenticatedAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // Los roles vienen del JWT de Keycloak, no de la BD
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!isAdmin) {
            throw new SecurityException("Acceso denegado: se requiere rol ADMIN");
        }

        // auth.getName() retorna el email (configurado en KeycloakJwtAuthConverter)
        return usersRepository.findByEmail(auth.getName())
                .orElseThrow(
                        () -> new IllegalArgumentException("Usuario autenticado no encontrado en el sistema local"));
    }

    // Extrae el rol de negocio del JWT de Keycloak (ADMIN, DPO o JEFE_DOMINIO).
    // Filtra roles técnicos de Keycloak como offline_access, uma_authorization,
    // etc.
    private static final java.util.Set<String> BUSINESS_ROLES = java.util.Set.of("ADMIN", "DPO", "JEFE_DOMINIO", "USER",
            "TITULAR");

    private String getActorRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .filter(BUSINESS_ROLES::contains)
                .findFirst()
                .orElse("UNKNOWN");
    }
}
