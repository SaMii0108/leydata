package com.leydata.backend.user.application.service;

import com.leydata.backend.audit.application.dto.AuditContext;
import com.leydata.backend.audit.application.service.AuditService;
import com.leydata.backend.entity.Domains;
import com.leydata.backend.entity.Role;
import com.leydata.backend.entity.UserDomains;
import com.leydata.backend.entity.Users;
import com.leydata.backend.entity.UsersRole;
import com.leydata.backend.orgdomain.infrastructure.persistence.DomainsRepository;
import com.leydata.backend.orgdomain.infrastructure.persistence.UserDomainsRepository;
import com.leydata.backend.security.KeycloakAdminService;
import com.leydata.backend.shared.SecurityContextHelper;
import com.leydata.backend.user.application.dto.CreateUserRequest;
import com.leydata.backend.user.application.dto.UpdateUserByAdminRequest;
import com.leydata.backend.user.application.dto.UserResponse;
import com.leydata.backend.user.domain.exception.UserAlreadyExistsException;
import com.leydata.backend.user.domain.exception.UserNotFoundException;
import com.leydata.backend.user.infrastructure.persistence.RoleRepository;
import com.leydata.backend.user.infrastructure.persistence.UsersRepository;
import com.leydata.backend.user.infrastructure.persistence.UsersRoleRepository;
import lombok.RequiredArgsConstructor;
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
    private final SecurityContextHelper securityContextHelper;

    // CREAR USUARIO
    // Flujo atómico: primero crea en Keycloak (obtiene el keycloak_id), luego guarda en BD local.
    // Si la BD falla, elimina el usuario de Keycloak como compensación (transacción distribuida manual).
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {

        if (usersRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new UserAlreadyExistsException("Ya existe un usuario con el email: " + request.getEmail());
        }

        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("La contraseña es obligatoria para crear un usuario");
        }

        Role role = roleRepository.findByCode(request.getRoleCode())
                .orElseThrow(() -> new IllegalArgumentException("Rol no encontrado: " + request.getRoleCode()));

        // Paso 1: crear el usuario en Keycloak y obtener su ID único (sub del JWT futuro).
        // Si esto falla, no se toca la BD local — no hay nada que compensar.
        String keycloakId = keycloakAdminService.createUser(
                request.getEmail(),
                request.getName(),
                request.getRoleCode(),
                request.getPassword());

        Users savedUser;
        try {
            // Paso 2: guardar en BD local con el keycloak_id recién obtenido.
            // Si esto falla, el catch elimina al usuario de Keycloak antes de relanzar.
            Users user = new Users();
            user.setKeycloakId(keycloakId);
            user.setEmail(request.getEmail());
            user.setName(request.getName());
            user.setActive(true);
            user.setBlocked(false);
            user.setMustChangePassword(false);
            user.setCreatedAt(LocalDateTime.now());

            savedUser = usersRepository.save(user);

            UsersRole usersRole = new UsersRole();
            UsersRole.UsersRoleId roleId = new UsersRole.UsersRoleId();
            roleId.setUserId(savedUser.getId());
            roleId.setRoleId(role.getId());
            usersRole.setId(roleId);
            usersRole.setUser(savedUser);
            usersRole.setRole(role);
            usersRoleRepository.save(usersRole);

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
            // Compensación: el usuario quedó en Keycloak pero no en nuestra BD.
            // Lo eliminamos de Keycloak para mantener consistencia entre ambos sistemas.
            keycloakAdminService.deleteUser(keycloakId);
            throw new RuntimeException("Error al registrar el usuario en el sistema: " + e.getMessage(), e);
        }

        securityContextHelper.requireAdmin();

        auditService.log(AuditContext.builder()
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
                .actorId(securityContextHelper.getKeycloakId())
                .actorRole(securityContextHelper.getActorRole())
                .build());

        return new UserResponse(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getName(),
                savedUser.getActive(),
                savedUser.getBlocked(),
                List.of(request.getRoleCode()),
                List.of());
    }

    // EDITAR USUARIO (ADMIN)
    @Transactional
    public UserResponse updateUserByAdmin(UUID userId, UpdateUserByAdminRequest request) {
        securityContextHelper.requireAdmin();

        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado: " + userId));

        if (Boolean.TRUE.equals(user.getBlocked())) {
            throw new IllegalStateException("No se puede modificar un usuario bloqueado permanentemente");
        }

        Map<String, Object> oldData = Map.of(
                "name", user.getName() != null ? user.getName() : "",
                "roles", user.getUserRoles().stream().map(ur -> ur.getRole().getCode()).toList());

        if (request.getName() != null && !request.getName().isBlank()) {
            user.setName(request.getName());
        }

        if (request.getRoleCodes() != null) {
            changeUserRoles(user, request.getRoleCodes());
        }

        if (request.getDomainIds() != null) {
            assignUserDomains(user, request.getDomainIds());
        }

        Users saved = usersRepository.save(user);

        auditService.log(AuditContext.builder()
                .tableName("users")
                .recordId(saved.getId())
                .action("EDITAR_USUARIO")
                .oldData(oldData)
                .newData(Map.of(
                        "name", saved.getName() != null ? saved.getName() : "",
                        "roles", saved.getUserRoles().stream().map(ur -> ur.getRole().getCode()).toList()))
                .actorId(securityContextHelper.getKeycloakId())
                .actorRole(securityContextHelper.getActorRole())
                .build());

        return UserResponse.from(saved);
    }

    // DESACTIVAR USUARIO
    @Transactional
    public UserResponse deactivateUser(UUID userId) {
        securityContextHelper.requireAdmin();

        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado: " + userId));

        if (Boolean.TRUE.equals(user.getBlocked())) {
            throw new IllegalStateException("No se puede desactivar un usuario bloqueado permanentemente");
        }

        if (!user.getActive()) {
            throw new IllegalStateException("El usuario ya está desactivado");
        }

        user.setActive(false);
        Users saved = usersRepository.save(user);

        auditService.log(AuditContext.builder()
                .tableName("users")
                .recordId(saved.getId())
                .action("DESACTIVAR_USUARIO")
                .oldData(Map.of("active", true))
                .newData(Map.of("active", false))
                .actorId(securityContextHelper.getKeycloakId())
                .actorRole(securityContextHelper.getActorRole())
                .build());

        return UserResponse.from(saved);
    }

    // REACTIVAR USUARIO
    @Transactional
    public UserResponse reactivateUser(UUID userId) {
        securityContextHelper.requireAdmin();

        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado: " + userId));

        if (Boolean.TRUE.equals(user.getBlocked())) {
            throw new IllegalStateException("No se puede reactivar un usuario bloqueado permanentemente");
        }

        if (user.getActive()) {
            throw new IllegalStateException("El usuario ya está activo");
        }

        user.setActive(true);
        Users saved = usersRepository.save(user);

        auditService.log(AuditContext.builder()
                .tableName("users")
                .recordId(saved.getId())
                .action("REACTIVAR_USUARIO")
                .oldData(Map.of("active", false))
                .newData(Map.of("active", true))
                .actorId(securityContextHelper.getKeycloakId())
                .actorRole(securityContextHelper.getActorRole())
                .build());

        return UserResponse.from(saved);
    }

    // BLOQUEO PERMANENTE (irreversible desde la API)
    @Transactional
    public UserResponse blockUser(UUID targetUserId) {
        securityContextHelper.requireAdmin();

        Users target = usersRepository.findById(targetUserId)
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado: " + targetUserId));

        if (Boolean.TRUE.equals(target.getBlocked())) {
            throw new IllegalStateException("El usuario ya está bloqueado permanentemente");
        }

        Map<String, Object> oldData = Map.of(
                "active", target.getActive(),
                "blocked", target.getBlocked());

        target.setBlocked(true);
        target.setActive(false);
        Users saved = usersRepository.save(target);

        auditService.log(AuditContext.builder()
                .tableName("users")
                .recordId(saved.getId())
                .action("BLOQUEAR_USUARIO")
                .oldData(oldData)
                .newData(Map.of("active", false, "blocked", true))
                .actorId(securityContextHelper.getKeycloakId())
                .actorRole(securityContextHelper.getActorRole())
                .build());

        return UserResponse.from(saved);
    }

    // LISTAR USUARIOS (solo ADMIN)
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return usersRepository.findAll().stream()
                .map(UserResponse::from)
                .toList();
    }

    // OBTENER USUARIO POR ID
    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        Users user = usersRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado: " + id));
        return UserResponse.from(user);
    }

    // Cambia los roles de un usuario. Si pierde JEFE_DOMINIO, se desvincula de sus dominios.
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

        user.getUserRoles().removeIf(ur -> !requestedRoleIds.contains(ur.getRole().getId()));

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

        if (!newRolesIncludeJefeDominio) {
            user.getUserDomains().clear();
        }
    }

    // Asigna dominios activos a un usuario. Si la lista está vacía, limpia dominios existentes.
    // Solo se exige rol JEFE_DOMINIO cuando se intenta asignar dominios específicos.
    private void assignUserDomains(Users user, List<UUID> domainIds) {
        if (domainIds.isEmpty()) {
            user.getUserDomains().clear();
            return;
        }

        boolean hasJefeDominio = user.getUserRoles().stream()
                .anyMatch(ur -> "JEFE_DOMINIO".equals(ur.getRole().getCode()));

        if (!hasJefeDominio) {
            throw new IllegalArgumentException("No se pueden asignar dominios: el usuario no tiene rol JEFE_DOMINIO");
        }

        Set<UUID> requestedDomainIds = new HashSet<>(domainIds);
        Set<UUID> currentDomainIds = user.getUserDomains().stream()
                .map(ud -> ud.getId().getDomainId())
                .collect(Collectors.toSet());

        user.getUserDomains().removeIf(ud -> !requestedDomainIds.contains(ud.getId().getDomainId()));

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
}
