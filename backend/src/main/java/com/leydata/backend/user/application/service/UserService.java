package com.leydata.backend.user.application.service;

import com.leydata.backend.audit.application.dto.AuditContext;
import com.leydata.backend.audit.application.service.AuditService;
import com.leydata.backend.entity.Domains;
import com.leydata.backend.entity.Users;
import com.leydata.backend.orgdomain.infrastructure.persistence.DomainsRepository;
import com.leydata.backend.security.KeycloakAdminService;
import com.leydata.backend.shared.SecurityContextHelper;
import com.leydata.backend.user.application.dto.CreateUserRequest;
import com.leydata.backend.user.application.dto.UpdateUserByAdminRequest;
import com.leydata.backend.user.application.dto.UserResponse;
import com.leydata.backend.user.domain.exception.UserAlreadyExistsException;
import com.leydata.backend.user.domain.exception.UserNotFoundException;
import com.leydata.backend.user.infrastructure.persistence.UsersRepository;
import com.leydata.backend.userdomain.domain.UserDomain;
import com.leydata.backend.userdomain.infrastructure.persistence.UserDomainRepository;
import com.leydata.backend.userstatus.domain.UserStatus;
import com.leydata.backend.userstatus.infrastructure.persistence.UserStatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UsersRepository usersRepository;
    private final DomainsRepository domainsRepository;
    private final UserDomainRepository userDomainRepository;
    private final UserStatusRepository userStatusRepository;
    private final AuditService auditService;
    private final KeycloakAdminService keycloakAdminService;
    private final SecurityContextHelper securityContextHelper;

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (usersRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new UserAlreadyExistsException("Ya existe un usuario con el email: " + request.getEmail());
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("La contrasena es obligatoria para crear un usuario");
        }

        String keycloakId = keycloakAdminService.createUser(
                request.getEmail(), request.getName(), request.getRoleCode(), request.getPassword());

        Users savedUser;
        try {
            Users user = new Users();
            user.setKeycloakId(keycloakId);
            user.setEmail(request.getEmail());
            user.setName(request.getName());
            user.setActive(true);
            user.setCreatedAt(LocalDateTime.now());
            savedUser = usersRepository.save(user);

            if (request.getDomainIds() != null && !request.getDomainIds().isEmpty()) {
                if (!"JEFE_DOMINIO".equals(request.getRoleCode())) {
                    throw new IllegalArgumentException("Solo los jefes de dominio pueden tener dominios asignados");
                }
                for (UUID domainId : request.getDomainIds()) {
                    Domains domain = domainsRepository.findById(domainId)
                            .orElseThrow(() -> new IllegalArgumentException("Dominio no encontrado: " + domainId));
                    if (!Boolean.TRUE.equals(domain.getActive())) {
                        throw new IllegalArgumentException("El dominio esta desactivado: " + domain.getName());
                    }
                    userDomainRepository.save(UserDomain.builder().keycloakId(keycloakId).domain(domain).build());
                }
            }
        } catch (Exception e) {
            keycloakAdminService.deleteUser(keycloakId);
            throw new RuntimeException("Error al registrar el usuario en el sistema: " + e.getMessage(), e);
        }

        securityContextHelper.requireAdmin();
        auditService.log(AuditContext.builder()
                .tableName("users").recordId(savedUser.getId()).action("CREAR_USUARIO")
                .oldData(null)
                .newData(Map.of("id", savedUser.getId(), "email", savedUser.getEmail(),
                        "name", savedUser.getName(), "role", request.getRoleCode(), "active", savedUser.getActive()))
                .actorId(securityContextHelper.getKeycloakId()).actorRole(securityContextHelper.getActorRole()).build());

        List<String> roles = List.of(request.getRoleCode());
        return buildResponse(savedUser, roles);
    }

    @Transactional
    public UserResponse updateUserByAdmin(UUID userId, UpdateUserByAdminRequest request) {
        securityContextHelper.requireAdmin();
        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado: " + userId));
        if (isBlocked(user.getKeycloakId()))
            throw new IllegalStateException("No se puede modificar un usuario bloqueado permanentemente");

        List<String> currentRoles = keycloakAdminService.getUserRoles(user.getKeycloakId());
        Map<String, Object> oldData = Map.of(
                "name", user.getName() != null ? user.getName() : "",
                "email", user.getEmail() != null ? user.getEmail() : "",
                "roles", currentRoles);

        String newName = (request.getName() != null && !request.getName().isBlank()) ? request.getName() : null;
        String newEmail = null;
        if (request.getEmail() != null && !request.getEmail().isBlank()
                && !request.getEmail().equalsIgnoreCase(user.getEmail())) {
            if (usersRepository.findByEmail(request.getEmail()).isPresent())
                throw new UserAlreadyExistsException("Ya existe un usuario con el email: " + request.getEmail());
            newEmail = request.getEmail();
        }

        if (newName != null || newEmail != null) {
            keycloakAdminService.updateUserProfile(user.getKeycloakId(), newEmail, newName);
            if (newName != null) user.setName(newName);
            if (newEmail != null) user.setEmail(newEmail);
        }

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            // temporary=true: Keycloak fuerza al usuario a cambiar la contraseña en su próximo login
            keycloakAdminService.resetPassword(user.getKeycloakId(), request.getPassword(), true);
        }

        List<String> newRoles = currentRoles;
        if (request.getRoleCodes() != null && !request.getRoleCodes().isEmpty()) {
            boolean hadJefe = currentRoles.contains("JEFE_DOMINIO");
            boolean willHaveJefe = request.getRoleCodes().contains("JEFE_DOMINIO");
            keycloakAdminService.updateUserRoles(user.getKeycloakId(), request.getRoleCodes());
            newRoles = request.getRoleCodes();
            if (hadJefe && !willHaveJefe) {
                userDomainRepository.deleteByKeycloakId(user.getKeycloakId());
            }
        }
        if (request.getDomainIds() != null) {
            assignUserDomains(user, newRoles, request.getDomainIds());
        }

        Users saved = usersRepository.save(user);
        auditService.log(AuditContext.builder()
                .tableName("users").recordId(saved.getId()).action("EDITAR_USUARIO")
                .oldData(oldData)
                .newData(Map.of("name", saved.getName() != null ? saved.getName() : "",
                        "email", saved.getEmail() != null ? saved.getEmail() : "",
                        "roles", newRoles,
                        "passwordReset", request.getPassword() != null && !request.getPassword().isBlank()))
                .actorId(securityContextHelper.getKeycloakId()).actorRole(securityContextHelper.getActorRole()).build());
        return buildResponse(saved, newRoles);
    }

    @Transactional
    public UserResponse deactivateUser(UUID userId) {
        securityContextHelper.requireAdmin();
        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado: " + userId));
        if (user.getKeycloakId().equals(securityContextHelper.getKeycloakId()))
            throw new IllegalStateException("Un administrador no puede desactivarse a sí mismo");
        List<String> targetRoles = keycloakAdminService.getUserRoles(user.getKeycloakId());
        if (targetRoles.contains("ADMIN"))
            throw new IllegalStateException("No se puede desactivar a otro administrador");
        if (isBlocked(user.getKeycloakId()))
            throw new IllegalStateException("No se puede desactivar un usuario bloqueado permanentemente");
        if (!user.getActive())
            throw new IllegalStateException("El usuario ya esta desactivado");
        keycloakAdminService.disableUser(user.getKeycloakId());
        user.setActive(false);
        Users saved = usersRepository.save(user);
        auditService.log(AuditContext.builder().tableName("users").recordId(saved.getId())
                .action("DESACTIVAR_USUARIO").oldData(Map.of("active", true)).newData(Map.of("active", false))
                .actorId(securityContextHelper.getKeycloakId()).actorRole(securityContextHelper.getActorRole()).build());
        List<String> roles = keycloakAdminService.getUserRoles(saved.getKeycloakId());
        return buildResponse(saved, roles);
    }

    @Transactional
    public UserResponse reactivateUser(UUID userId) {
        securityContextHelper.requireAdmin();
        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado: " + userId));
        if (isBlocked(user.getKeycloakId()))
            throw new IllegalStateException("No se puede reactivar un usuario bloqueado permanentemente");
        if (user.getActive())
            throw new IllegalStateException("El usuario ya esta activo");
        keycloakAdminService.enableUser(user.getKeycloakId());
        user.setActive(true);
        Users saved = usersRepository.save(user);
        auditService.log(AuditContext.builder().tableName("users").recordId(saved.getId())
                .action("REACTIVAR_USUARIO").oldData(Map.of("active", false)).newData(Map.of("active", true))
                .actorId(securityContextHelper.getKeycloakId()).actorRole(securityContextHelper.getActorRole()).build());
        List<String> roles = keycloakAdminService.getUserRoles(saved.getKeycloakId());
        return buildResponse(saved, roles);
    }

    @Transactional
    public UserResponse blockUser(UUID targetUserId) {
        securityContextHelper.requireAdmin();
        Users target = usersRepository.findById(targetUserId)
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado: " + targetUserId));
        if (target.getKeycloakId().equals(securityContextHelper.getKeycloakId()))
            throw new IllegalStateException("Un administrador no puede bloquearse a sí mismo");
        List<String> targetRoles = keycloakAdminService.getUserRoles(target.getKeycloakId());
        if (targetRoles.contains("ADMIN"))
            throw new IllegalStateException("No se puede bloquear a otro administrador");
        if (isBlocked(target.getKeycloakId()))
            throw new IllegalStateException("El usuario ya esta bloqueado permanentemente");
        // Deshabilitar en Keycloak: impide nuevos logins y renovación de tokens
        keycloakAdminService.disableUser(target.getKeycloakId());
        // Registrar en user_status para auditoría y para cortar JWTs válidos existentes vía UserStatusFilter
        userStatusRepository.save(UserStatus.builder()
                .keycloakId(target.getKeycloakId()).blocked(true).blockedAt(LocalDateTime.now()).build());
        target.setActive(false);
        Users saved = usersRepository.save(target);
        auditService.log(AuditContext.builder().tableName("users").recordId(saved.getId())
                .action("BLOQUEAR_USUARIO")
                .oldData(Map.of("active", true, "blocked", false))
                .newData(Map.of("active", false, "blocked", true))
                .actorId(securityContextHelper.getKeycloakId()).actorRole(securityContextHelper.getActorRole()).build());
        List<String> roles = keycloakAdminService.getUserRoles(saved.getKeycloakId());
        return buildResponse(saved, roles);
    }

    // Lista usuarios desde Keycloak y aplica filtros opcionales independientes.
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers(String search, String status, String role) {
        List<Map<String, Object>> kcUsers = keycloakAdminService.listUsers(search);
        return kcUsers.stream().map(kcUser -> {
            String kcId = (String) kcUser.get("keycloakId");
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) kcUser.get("roleCodes");
            return usersRepository.findByKeycloakId(kcId)
                    .map(local -> buildResponse(local, roles))
                    .orElse(null);
        })
        .filter(r -> r != null)
        .filter(r -> matchesStatus(r, status))
        .filter(r -> role == null || role.isBlank() || r.getRoles().contains(role))
        .toList();
    }

    private boolean matchesStatus(UserResponse r, String status) {
        if (status == null || status.isBlank()) return true;
        return switch (status.toLowerCase()) {
            case "blocked"  -> r.getBlocked();
            case "inactive" -> !r.getActive() && !r.getBlocked();
            case "active"   -> r.getActive() && !r.getBlocked();
            default         -> true;
        };
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        Users user = usersRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado: " + id));
        List<String> roles = keycloakAdminService.getUserRoles(user.getKeycloakId());
        return buildResponse(user, roles);
    }

    private boolean isBlocked(String keycloakId) {
        if (keycloakId == null) return false;
        return userStatusRepository.findById(keycloakId).map(UserStatus::getBlocked).orElse(false);
    }

    private UserResponse buildResponse(Users user, List<String> roles) {
        boolean blocked = isBlocked(user.getKeycloakId());
        List<String> domainNames = (user.getKeycloakId() != null)
                ? userDomainRepository.findByKeycloakId(user.getKeycloakId()).stream()
                        .filter(ud -> Boolean.TRUE.equals(ud.getDomain().getActive()))
                        .map(ud -> ud.getDomain().getName()).toList()
                : List.of();
        return new UserResponse(user.getId(), user.getEmail(), user.getName(),
                user.getActive(), blocked, roles, domainNames);
    }

    private void assignUserDomains(Users user, List<String> currentRoles, List<UUID> domainIds) {
        if (domainIds.isEmpty()) {
            userDomainRepository.deleteByKeycloakId(user.getKeycloakId());
            return;
        }
        if (!currentRoles.contains("JEFE_DOMINIO")) {
            throw new IllegalArgumentException("No se pueden asignar dominios: el usuario no tiene rol JEFE_DOMINIO");
        }
        List<UserDomain> current = userDomainRepository.findByKeycloakId(user.getKeycloakId());
        current.stream().filter(ud -> !domainIds.contains(ud.getDomain().getId()))
                .forEach(ud -> userDomainRepository.deleteByKeycloakIdAndDomainId(
                        user.getKeycloakId(), ud.getDomain().getId()));
        for (UUID domainId : domainIds) {
            if (!userDomainRepository.existsByKeycloakIdAndDomainId(user.getKeycloakId(), domainId)) {
                Domains domain = domainsRepository.findById(domainId)
                        .orElseThrow(() -> new IllegalArgumentException("Dominio no encontrado: " + domainId));
                if (!Boolean.TRUE.equals(domain.getActive()))
                    throw new IllegalArgumentException("El dominio esta desactivado: " + domain.getName());
                userDomainRepository.save(UserDomain.builder().keycloakId(user.getKeycloakId()).domain(domain).build());
            }
        }
    }
}
