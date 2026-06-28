package com.leydata.backend.user.application.service;

import com.leydata.backend.audit.application.dto.AuditContext;
import com.leydata.backend.audit.application.service.AuditService;
import com.leydata.backend.entity.Domains;
import com.leydata.backend.orgdomain.infrastructure.persistence.DomainsRepository;
import com.leydata.backend.security.KeycloakAdminService;
import com.leydata.backend.shared.SecurityContextHelper;
import com.leydata.backend.user.application.dto.CreateUserRequest;
import com.leydata.backend.user.application.dto.UpdateUserByAdminRequest;
import com.leydata.backend.user.application.dto.UserResponse;
import com.leydata.backend.user.domain.exception.UserNotFoundException;
import com.leydata.backend.userdomain.domain.UserDomain;
import com.leydata.backend.userdomain.infrastructure.persistence.UserDomainRepository;
import com.leydata.backend.userstatus.domain.UserStatus;
import com.leydata.backend.userstatus.infrastructure.persistence.UserStatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final DomainsRepository domainsRepository;
    private final UserDomainRepository userDomainRepository;
    private final UserStatusRepository userStatusRepository;
    private final StringRedisTemplate redisTemplate;
    private final AuditService auditService;
    private final KeycloakAdminService keycloakAdminService;
    private final SecurityContextHelper securityContextHelper;

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        securityContextHelper.requireAdmin();
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("La contraseña es obligatoria para crear un usuario");
        }

        // Keycloak lanza 409 si el email ya existe — no necesitamos chequeo local previo
        String keycloakId = keycloakAdminService.createUser(
                request.getEmail(), request.getName(), request.getRoleCode(), request.getPassword());

        try {
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
                    userDomainRepository.save(UserDomain.builder().keycloakId(keycloakId).domain(domain).build());
                }
            }
        } catch (Exception e) {
            keycloakAdminService.deleteUser(keycloakId);
            throw new RuntimeException("Error al registrar el usuario en el sistema: " + e.getMessage(), e);
        }

        auditService.log(AuditContext.builder()
                .tableName("users").recordId(UUID.fromString(keycloakId)).action("CREAR_USUARIO")
                .oldData(null)
                .newData(Map.of("keycloakId", keycloakId, "email", request.getEmail(),
                        "name", request.getName(), "role", request.getRoleCode()))
                .actorId(securityContextHelper.getKeycloakId()).actorRole(securityContextHelper.getActorRole()).build());

        List<String> roles = List.of(request.getRoleCode());
        return buildResponse(keycloakId, request.getEmail(), request.getName(), true, roles);
    }

    @Transactional
    public UserResponse updateUserByAdmin(String keycloakId, UpdateUserByAdminRequest request) {
        securityContextHelper.requireAdmin();
        if (isBlocked(keycloakId)) {
            throw new IllegalStateException("No se puede modificar un usuario bloqueado permanentemente");
        }

        Map<String, Object> kcUser = keycloakAdminService.getUser(keycloakId);
        List<String> currentRoles = keycloakAdminService.getUserRoles(keycloakId);
        Map<String, Object> oldData = Map.of(
                "name",  kcUser.getOrDefault("name", ""),
                "email", kcUser.getOrDefault("email", ""),
                "roles", currentRoles);

        String newName  = (request.getName() != null && !request.getName().isBlank())  ? request.getName()  : null;
        String newEmail = (request.getEmail() != null && !request.getEmail().isBlank()) ? request.getEmail() : null;

        if (newName != null || newEmail != null) {
            keycloakAdminService.updateUserProfile(keycloakId, newEmail, newName);
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            keycloakAdminService.resetPassword(keycloakId, request.getPassword(), true);
        }

        List<String> newRoles = currentRoles;
        if (request.getRoleCodes() != null && !request.getRoleCodes().isEmpty()) {
            boolean hadJefe  = currentRoles.contains("JEFE_DOMINIO");
            boolean willHave = request.getRoleCodes().contains("JEFE_DOMINIO");
            keycloakAdminService.updateUserRoles(keycloakId, request.getRoleCodes());
            newRoles = request.getRoleCodes();
            if (hadJefe && !willHave) {
                userDomainRepository.deleteByKeycloakId(keycloakId);
            }
        }
        if (request.getDomainIds() != null) {
            assignUserDomains(keycloakId, newRoles, request.getDomainIds());
        }

        Map<String, Object> updatedKc = keycloakAdminService.getUser(keycloakId);
        auditService.log(AuditContext.builder()
                .tableName("users").recordId(UUID.fromString(keycloakId)).action("EDITAR_USUARIO")
                .oldData(oldData)
                .newData(Map.of("name",  updatedKc.getOrDefault("name", ""),
                        "email",         updatedKc.getOrDefault("email", ""),
                        "roles",         newRoles,
                        "passwordReset", request.getPassword() != null && !request.getPassword().isBlank()))
                .actorId(securityContextHelper.getKeycloakId()).actorRole(securityContextHelper.getActorRole()).build());

        return buildResponse(keycloakId, (String) updatedKc.get("email"), (String) updatedKc.get("name"),
                Boolean.TRUE.equals(updatedKc.get("enabled")), newRoles);
    }

    @Transactional
    public UserResponse deactivateUser(String keycloakId) {
        securityContextHelper.requireAdmin();
        if (keycloakId.equals(securityContextHelper.getKeycloakId())) {
            throw new IllegalStateException("Un administrador no puede desactivarse a sí mismo");
        }
        List<String> targetRoles = keycloakAdminService.getUserRoles(keycloakId);
        if (targetRoles.contains("ADMIN")) {
            throw new IllegalStateException("No se puede desactivar a otro administrador");
        }
        if (isBlocked(keycloakId)) {
            throw new IllegalStateException("No se puede desactivar un usuario bloqueado permanentemente");
        }
        Map<String, Object> kcUser = keycloakAdminService.getUser(keycloakId);
        if (!Boolean.TRUE.equals(kcUser.get("enabled"))) {
            throw new IllegalStateException("El usuario ya está desactivado");
        }
        keycloakAdminService.disableUser(keycloakId);
        auditService.log(AuditContext.builder().tableName("users").recordId(UUID.fromString(keycloakId))
                .action("DESACTIVAR_USUARIO").oldData(Map.of("active", true)).newData(Map.of("active", false))
                .actorId(securityContextHelper.getKeycloakId()).actorRole(securityContextHelper.getActorRole()).build());
        return buildResponse(keycloakId, (String) kcUser.get("email"), (String) kcUser.get("name"), false, targetRoles);
    }

    @Transactional
    public UserResponse reactivateUser(String keycloakId) {
        securityContextHelper.requireAdmin();
        if (isBlocked(keycloakId)) {
            throw new IllegalStateException("No se puede reactivar un usuario bloqueado permanentemente");
        }
        Map<String, Object> kcUser = keycloakAdminService.getUser(keycloakId);
        if (Boolean.TRUE.equals(kcUser.get("enabled"))) {
            throw new IllegalStateException("El usuario ya está activo");
        }
        keycloakAdminService.enableUser(keycloakId);
        List<String> roles = keycloakAdminService.getUserRoles(keycloakId);
        auditService.log(AuditContext.builder().tableName("users").recordId(UUID.fromString(keycloakId))
                .action("REACTIVAR_USUARIO").oldData(Map.of("active", false)).newData(Map.of("active", true))
                .actorId(securityContextHelper.getKeycloakId()).actorRole(securityContextHelper.getActorRole()).build());
        return buildResponse(keycloakId, (String) kcUser.get("email"), (String) kcUser.get("name"), true, roles);
    }

    @Transactional
    public UserResponse blockUser(String keycloakId) {
        securityContextHelper.requireAdmin();
        if (keycloakId.equals(securityContextHelper.getKeycloakId())) {
            throw new IllegalStateException("Un administrador no puede bloquearse a sí mismo");
        }
        List<String> targetRoles = keycloakAdminService.getUserRoles(keycloakId);
        if (targetRoles.contains("ADMIN")) {
            throw new IllegalStateException("No se puede bloquear a otro administrador");
        }
        if (isBlocked(keycloakId)) {
            throw new IllegalStateException("El usuario ya está bloqueado permanentemente");
        }
        Map<String, Object> kcUser = keycloakAdminService.getUser(keycloakId);
        keycloakAdminService.disableUser(keycloakId);
        userStatusRepository.save(UserStatus.builder()
                .keycloakId(keycloakId).blocked(true).blockedAt(LocalDateTime.now()).build());
        redisTemplate.opsForValue().set("user:" + keycloakId + ":blocked", "true");
        auditService.log(AuditContext.builder().tableName("users").recordId(UUID.fromString(keycloakId))
                .action("BLOQUEAR_USUARIO")
                .oldData(Map.of("active", true, "blocked", false))
                .newData(Map.of("active", false, "blocked", true))
                .actorId(securityContextHelper.getKeycloakId()).actorRole(securityContextHelper.getActorRole()).build());
        return buildResponse(keycloakId, (String) kcUser.get("email"), (String) kcUser.get("name"), false, targetRoles);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers(String search, String status, String role) {
        return keycloakAdminService.listUsers(search).stream()
                .map(kcUser -> {
                    String kcId  = (String) kcUser.get("keycloakId");
                    @SuppressWarnings("unchecked")
                    List<String> roles = (List<String>) kcUser.get("roleCodes");
                    boolean active  = Boolean.TRUE.equals(kcUser.get("enabled"));
                    return buildResponse(kcId, (String) kcUser.get("email"), (String) kcUser.get("name"), active, roles);
                })
                .filter(r -> matchesStatus(r, status))
                .filter(r -> role == null || role.isBlank() || r.getRoles().contains(role))
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getUserByKeycloakId(String keycloakId) {
        Map<String, Object> kcUser = keycloakAdminService.getUser(keycloakId);
        List<String> roles = keycloakAdminService.getUserRoles(keycloakId);
        return buildResponse(keycloakId, (String) kcUser.get("email"), (String) kcUser.get("name"),
                Boolean.TRUE.equals(kcUser.get("enabled")), roles);
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

    private boolean isBlocked(String keycloakId) {
        if (keycloakId == null) return false;
        return userStatusRepository.findById(keycloakId).map(UserStatus::getBlocked).orElse(false);
    }

    private UserResponse buildResponse(String keycloakId, String email, String name,
                                       boolean active, List<String> roles) {
        boolean blocked = isBlocked(keycloakId);
        List<String> domainNames = userDomainRepository.findByKeycloakId(keycloakId).stream()
                .filter(ud -> Boolean.TRUE.equals(ud.getDomain().getActive()))
                .map(ud -> ud.getDomain().getName())
                .toList();
        return new UserResponse(keycloakId, email, name, active, blocked, roles, domainNames);
    }

    private void assignUserDomains(String keycloakId, List<String> currentRoles, List<UUID> domainIds) {
        if (domainIds.isEmpty()) {
            userDomainRepository.deleteByKeycloakId(keycloakId);
            return;
        }
        if (!currentRoles.contains("JEFE_DOMINIO")) {
            throw new IllegalArgumentException("No se pueden asignar dominios: el usuario no tiene rol JEFE_DOMINIO");
        }
        List<UserDomain> current = userDomainRepository.findByKeycloakId(keycloakId);
        current.stream()
                .filter(ud -> !domainIds.contains(ud.getDomain().getId()))
                .forEach(ud -> userDomainRepository.deleteByKeycloakIdAndDomainId(keycloakId, ud.getDomain().getId()));
        for (UUID domainId : domainIds) {
            if (!userDomainRepository.existsByKeycloakIdAndDomainId(keycloakId, domainId)) {
                Domains domain = domainsRepository.findById(domainId)
                        .orElseThrow(() -> new IllegalArgumentException("Dominio no encontrado: " + domainId));
                if (!Boolean.TRUE.equals(domain.getActive())) {
                    throw new IllegalArgumentException("El dominio está desactivado: " + domain.getName());
                }
                userDomainRepository.save(UserDomain.builder().keycloakId(keycloakId).domain(domain).build());
            }
        }
    }
}
