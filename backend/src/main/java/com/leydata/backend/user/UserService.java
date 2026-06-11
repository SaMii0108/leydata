package com.leydata.backend.user;

import com.leydata.backend.audit.AuditContext;
import com.leydata.backend.audit.AuditService;
import com.leydata.backend.entity.Domains;
import com.leydata.backend.entity.Role;
import com.leydata.backend.entity.UserDomains;
import com.leydata.backend.entity.Users;
import com.leydata.backend.entity.UsersRole;
import com.leydata.backend.domain.DomainsRepository;
import com.leydata.backend.domain.UserDomainsRepository;
import com.leydata.backend.security.KeycloakAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UsersRepository usersRepository;
    private final RoleRepository roleRepository;
    private final UsersRoleRepository usersRoleRepository;
    private final DomainsRepository domainsRepository;
    private final UserDomainsRepository userDomainsRepository;
    private final AuditService auditService;
    private final KeycloakAdminService keycloakAdminService;

    // CREAR USUARIO
    //Flujo atómico: primero crea en Keycloak (obtiene el keycloak_id), luego guarda en BD local.
    //Si la BD falla, elimina el usuario de Keycloak como compensación (transacción distribuida manual).
    @Transactional
    public Users createUser(CreateUserRequest request) {

        if (usersRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Ya existe un usuario con el email: " + request.getEmail());
        }

        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("La contraseña es obligatoria para crear un usuario");
        }

        Role role = roleRepository.findByCode(request.getRoleCode())
                .orElseThrow(() -> new IllegalArgumentException("Rol no encontrado: " + request.getRoleCode()));

        //Paso 1: crear el usuario en Keycloak y obtener su ID único (sub del JWT futuro).
        //Si esto falla, no se toca la BD local — no hay nada que compensar.
        String keycloakId = keycloakAdminService.createUser(
                request.getEmail(),
                request.getName(),
                request.getRoleCode(),
                request.getPassword()
        );

        Users savedUser;
        try {
            //Paso 2: guardar en BD local con el keycloak_id recién obtenido.
            //Si esto falla, el catch elimina al usuario de Keycloak antes de relanzar.
            Users user = new Users();
            user.setKeycloakId(keycloakId);
            user.setEmail(request.getEmail());
            user.setName(request.getName());
            user.setActive(true);
            user.setBlocked(false);
            user.setMustChangePassword(false);
            user.setCreatedAt(LocalDateTime.now());
            //La contraseña nunca se persiste aquí — Keycloak la gestiona

            savedUser = usersRepository.save(user);

            //Asignar rol en nuestra BD (espejo del realm role que ya asignamos en Keycloak)
            UsersRole usersRole = new UsersRole();
            UsersRole.UsersRoleId roleId = new UsersRole.UsersRoleId();
            roleId.setUserId(savedUser.getId());
            roleId.setRoleId(role.getId());
            usersRole.setId(roleId);
            usersRole.setUser(savedUser);
            usersRole.setRole(role);
            usersRoleRepository.save(usersRole);

            //Asignar dominios si el rol es JEFE_DOMINIO
            if (request.getDomainIds() != null && !request.getDomainIds().isEmpty()) {
                if (!"JEFE_DOMINIO".equals(request.getRoleCode())) {
                    throw new IllegalArgumentException("Solo los jefes de dominio pueden tener dominios asignados");
                }
                for (UUID domainId : request.getDomainIds()) {
                    Domains domain = domainsRepository.findById(domainId)
                            .orElseThrow(() -> new IllegalArgumentException("Dominio no encontrado: " + domainId));

                    if (!Boolean.TRUE.equals(domain.getActive())) {
                        throw new IllegalArgumentException("El dominio está desactivado: " + domain.getName());
                    }

                    UserDomains userDomain = new UserDomains();
                    UserDomains.UserDomainsId udId = new UserDomains.UserDomainsId();
                    udId.setUserId(savedUser.getId());
                    udId.setDomainId(domain.getId());
                    userDomain.setId(udId);
                    userDomain.setUser(savedUser);
                    userDomain.setDomain(domain);
                    userDomainsRepository.save(userDomain);
                }
            }

        } catch (Exception e) {
            //Compensación: si la BD falló, el usuario quedó en Keycloak pero no en nuestra BD.
            //Lo eliminamos de Keycloak para mantener consistencia entre ambos sistemas.
            keycloakAdminService.deleteUser(keycloakId);
            throw new RuntimeException("Error al registrar el usuario en el sistema: " + e.getMessage(), e);
        }

        Users admin = getAuthenticatedAdmin();

        //Registrar en auditoría: quién creó al usuario, con qué datos y desde dónde
        auditService.tryLog(AuditContext.builder()
                .tableName("users")
                .recordId(savedUser.getId())
                .action("CREAR_USUARIO")
                .oldData(null)
                .newData(Map.of(
                        "id", savedUser.getId(),
                        "email", savedUser.getEmail(),
                        "name", savedUser.getName(),
                        "role", request.getRoleCode(),
                        "active", savedUser.getActive()))
                .actorId(admin.getId())
                .actorRole(getActorRole())
                .build());

        return savedUser;
    }

    // EDITAR USUARIO (ADMIN)
    @Transactional
    public UserSummaryDto updateUserByAdmin(UUID userId, UpdateUserByAdminRequest request) {
        Users admin = getAuthenticatedAdmin();

        if (admin.getId().equals(userId)) {
            throw new IllegalArgumentException("El ADMIN no puede modificar su propio perfil desde este endpoint");
        }

        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + userId));

        if (Boolean.TRUE.equals(user.getBlocked())) {
            throw new IllegalStateException("No se puede modificar un usuario bloqueado permanentemente");
        }

        // Snapshot del estado anterior para auditoría
        Map<String, Object> oldData = Map.of(
                "name", user.getName() != null ? user.getName() : "",
                "roles", user.getUserRoles().stream().map(ur -> ur.getRole().getCode()).toList());

        // Actualizar nombre si se proporcionó
        if (request.getName() != null && !request.getName().isBlank()) {
            user.setName(request.getName());
        }

        // Actualizar roles si se proporcionaron
        if (request.getRoleCodes() != null) {
            changeUserRoles(user, request.getRoleCodes());
        }

        // Actualizar dominios si se proporcionaron
        if (request.getDomainIds() != null) {
            assignUserDomains(user, request.getDomainIds());
        }

        Users saved = usersRepository.save(user);

        // Registrar qué campos cambió el ADMIN
        auditService.tryLog(AuditContext.builder()
                .tableName("users")
                .recordId(saved.getId())
                .action("EDITAR_USUARIO")
                .oldData(oldData)
                .newData(Map.of(
                        "name", saved.getName() != null ? saved.getName() : "",
                        "roles", saved.getUserRoles().stream().map(ur -> ur.getRole().getCode()).toList()))
                .actorId(admin.getId())
                .actorRole(getActorRole())
                .build());

        return toSummaryDto(saved);
    }

    // DESACTIVAR USUARIO
    @Transactional
    public UserSummaryDto deactivateUser(UUID userId) {
        Users admin = getAuthenticatedAdmin();

        if (admin.getId().equals(userId)) {
            throw new IllegalArgumentException("El ADMIN no puede desactivarse a sí mismo");
        }

        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + userId));

        if (Boolean.TRUE.equals(user.getBlocked())) {
            throw new IllegalStateException("No se puede desactivar un usuario bloqueado permanentemente");
        }

        if (!user.getActive()) {
            throw new IllegalStateException("El usuario ya está desactivado");
        }

        user.setActive(false);
        Users saved = usersRepository.save(user);

        auditService.tryLog(AuditContext.builder()
                .tableName("users")
                .recordId(saved.getId())
                .action("DESACTIVAR_USUARIO")
                .oldData(Map.of("active", true))
                .newData(Map.of("active", false))
                .actorId(admin.getId())
                .actorRole(getActorRole())
                .build());

        return toSummaryDto(saved);
    }

    // REACTIVAR USUARIO
    @Transactional
    public UserSummaryDto reactivateUser(UUID userId) {
        Users admin = getAuthenticatedAdmin();

        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + userId));

        if (Boolean.TRUE.equals(user.getBlocked())) {
            throw new IllegalStateException("No se puede reactivar un usuario bloqueado permanentemente");
        }

        if (user.getActive()) {
            throw new IllegalStateException("El usuario ya está activo");
        }

        user.setActive(true);
        Users saved = usersRepository.save(user);

        auditService.tryLog(AuditContext.builder()
                .tableName("users")
                .recordId(saved.getId())
                .action("REACTIVAR_USUARIO")
                .oldData(Map.of("active", false))
                .newData(Map.of("active", true))
                .actorId(admin.getId())
                .actorRole(getActorRole())
                .build());

        return toSummaryDto(saved);
    }

    // BLOQUEO PERMANENTE (irreversible desde la API)
    @Transactional
    public UserSummaryDto blockUser(UUID targetUserId) {
        Users admin = getAuthenticatedAdmin();

        if (admin.getId().equals(targetUserId)) {
            throw new IllegalArgumentException("El ADMIN no puede bloquearse a sí mismo");
        }

        Users target = usersRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + targetUserId));

        if (Boolean.TRUE.equals(target.getBlocked())) {
            throw new IllegalStateException("El usuario ya está bloqueado permanentemente");
        }

        // Guardar estado anterior para auditoría
        Map<String, Object> oldData = Map.of(
                "active", target.getActive(),
                "blocked", target.getBlocked());

        target.setBlocked(true);
        target.setActive(false); // Un usuario bloqueado siempre queda inactivo
        Users saved = usersRepository.save(target);

        // Acción crítica: bloqueo permanente. Queda registrado con quién, cuándo y
        // desde dónde
        auditService.tryLog(AuditContext.builder()
                .tableName("users")
                .recordId(saved.getId())
                .action("BLOQUEAR_USUARIO")
                .oldData(oldData)
                .newData(Map.of("active", false, "blocked", true))
                .actorId(admin.getId())
                .actorRole(getActorRole())
                .build());

        return toSummaryDto(saved);
    }

    // LISTAR USUARIOS (solo ADMIN)
    @Transactional(readOnly = true)
    public List<UserSummaryDto> getAllUsers() {
        return usersRepository.findAll().stream()
                .map(this::toSummaryDto)
                .toList();
    }

    // OBTENER USUARIO POR ID
    @Transactional(readOnly = true)
    public UserSummaryDto getUserById(UUID id) {
        Users user = usersRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + id));
        return toSummaryDto(user);
    }

    // MÉTODOS ATÓMICOS PRIVADOS

    // Cambia los roles de un usuario. Si pierde JEFE_DOMINIO, se desvincula de sus
    // dominios
    private void changeUserRoles(Users user, List<String> roleCodes) {
        List<Role> requestedRoles = roleCodes.stream()
                .map(code -> roleRepository.findByCode(code)
                        .orElseThrow(() -> new IllegalArgumentException("Rol no encontrado: " + code)))
                .toList();

        boolean newRolesIncludeJefeDominio = requestedRoles.stream()
                .anyMatch(r -> "JEFE_DOMINIO".equals(r.getCode()));

        Set<UUID> requestedRoleIds = requestedRoles.stream()
                .map(Role::getId)
                .collect(Collectors.toSet());

        Set<UUID> currentRoleIds = user.getUserRoles().stream()
                .map(ur -> ur.getRole().getId())
                .collect(Collectors.toSet());

        // Remover roles que ya no se requieren
        user.getUserRoles().removeIf(ur -> !requestedRoleIds.contains(ur.getRole().getId()));

        // Agregar los roles nuevos
        for (Role role : requestedRoles) {
            if (!currentRoleIds.contains(role.getId())) {
                UsersRole newUsersRole = new UsersRole();
                UsersRole.UsersRoleId compositeId = new UsersRole.UsersRoleId();
                compositeId.setUserId(user.getId());
                compositeId.setRoleId(role.getId());
                newUsersRole.setId(compositeId);
                newUsersRole.setUser(user);
                newUsersRole.setRole(role);
                user.getUserRoles().add(newUsersRole);
            }
        }

        // Regla de negocio: si pierde el rol JEFE_DOMINIO, se desvincula de todos sus
        // dominios
        if (!newRolesIncludeJefeDominio) {
            user.getUserDomains().clear();
        }
    }

    // Asigna dominios activos a un usuario. Solo permitido si tiene rol
    // JEFE_DOMINIO
    private void assignUserDomains(Users user, List<UUID> domainIds) {
        boolean hasJefeDominio = user.getUserRoles().stream()
                .anyMatch(ur -> "JEFE_DOMINIO".equals(ur.getRole().getCode()));

        if (!hasJefeDominio) {
            throw new IllegalArgumentException("No se pueden asignar dominios: el usuario no tiene rol JEFE_DOMINIO");
        }

        Set<UUID> requestedDomainIds = new HashSet<>(domainIds);
        Set<UUID> currentDomainIds = user.getUserDomains().stream()
                .map(ud -> ud.getDomain().getId())
                .collect(Collectors.toSet());

        // Remover vínculos no requeridos
        user.getUserDomains().removeIf(ud -> !requestedDomainIds.contains(ud.getDomain().getId()));

        // Agregar solo los dominios nuevos
        for (UUID domainId : requestedDomainIds) {
            if (!currentDomainIds.contains(domainId)) {
                Domains domain = domainsRepository.findById(domainId)
                        .orElseThrow(() -> new IllegalArgumentException("Dominio no encontrado: " + domainId));

                if (!Boolean.TRUE.equals(domain.getActive())) {
                    throw new IllegalArgumentException("El dominio está desactivado: " + domain.getName());
                }

                UserDomains newUserDomain = new UserDomains();
                UserDomains.UserDomainsId compositeId = new UserDomains.UserDomainsId();
                compositeId.setUserId(user.getId());
                compositeId.setDomainId(domain.getId());
                newUserDomain.setId(compositeId);
                newUserDomain.setUser(user);
                newUserDomain.setDomain(domain);
                user.getUserDomains().add(newUserDomain);
            }
        }
    }

    // HELPERS DE AUTENTICACIÓN

    // Verifica que el usuario autenticado tiene rol ADMIN (desde el JWT de
    // Keycloak)
    // y retorna su entidad local para usarla en auditoría y lógica de negocio
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
    // Filtra roles técnicos de Keycloak como offline_access, uma_authorization, etc.
    private static final java.util.Set<String> BUSINESS_ROLES = java.util.Set.of("ADMIN", "DPO", "JEFE_DOMINIO", "USER", "TITULAR");
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

    private UserSummaryDto toSummaryDto(Users user) {
        return new UserSummaryDto(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getActive(),
                user.getBlocked(),
                user.getUserRoles().stream()
                        .map(ur -> ur.getRole().getCode())
                        .toList(),
                user.getUserDomains().stream()
                        .filter(ud -> Boolean.TRUE.equals(ud.getDomain().getActive()))
                        .map(ud -> ud.getDomain().getName())
                        .toList());
    }
}
