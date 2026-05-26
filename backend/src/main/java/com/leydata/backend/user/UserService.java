package com.leydata.backend.user;

import com.leydata.backend.entity.Domains;
import com.leydata.backend.entity.Role;
import com.leydata.backend.entity.UserDomains;
import com.leydata.backend.entity.Users;
import com.leydata.backend.entity.UsersRole;
import com.leydata.backend.domain.DomainsRepository;
import com.leydata.backend.domain.UserDomainsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final PasswordEncoder passwordEncoder;

    // CREAR USUARIO
    @Transactional
    public Users createUser(CreateUserRequest request) {

        if (usersRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Ya existe un usuario con el email: " + request.getEmail());
        }

        Role role = roleRepository.findByCode(request.getRoleCode())
                .orElseThrow(() -> new IllegalArgumentException("Rol no encontrado: " + request.getRoleCode()));

        Users user = new Users();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setName(request.getName());
        user.setActive(true);
        user.setBlocked(false);
        user.setCreatedAt(LocalDateTime.now());

        Users savedUser = usersRepository.save(user);

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

                // Validar que el dominio esté activo antes de asignarlo
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

        return savedUser;
    }

    // ACTUALIZAR PERFIL cualquier rol (sin email)
    @Transactional
    public Users updateUserProfile(String currentUserEmail, UpdateUserProfileRequest request) {

        Users user = usersRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            user.setName(request.getName());
        }

        return usersRepository.save(user);
    }

    // ACTUALIZAR PERFIL ADMIN solo password (sin email)
    @Transactional
    public Users updateAdminProfile(UpdateAdminProfileRequest request) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Users user = usersRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        boolean isAdmin = user.getUserRoles().stream()
                .anyMatch(ur -> "ADMIN".equals(ur.getRole().getCode()));
        if (!isAdmin) {
            throw new IllegalArgumentException("Solo el ADMIN puede usar este endpoint");
        }

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        return usersRepository.save(user);
    }

    // MÉTODO ACTUALIZACIÓN GENÉRICO
    // Actualiza un usuario de forma segura por ADMIN. Delegación a métodos atómicos
    // especializados.
    @Transactional
    public UserSummaryDto updateUserByAdmin(UUID userId, UpdateUserByAdminRequest request) {
        Users adminUser = getAuthenticatedAdmin();

        if (adminUser.getId().equals(userId)) {
            throw new IllegalArgumentException(
                    "El ADMIN no puede modificar su propio perfil desde este endpoint");
        }

        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + userId));

        // Validar que el usuario no esté bloqueado permanentemente
        if (Boolean.TRUE.equals(user.getBlocked())) {
            throw new IllegalStateException("No se puede modificar un usuario bloqueado permanentemente");
        }

        // Delegar a métodos atómicos
        if (request.getPassword() != null || request.getName() != null
                || request.getActive() != null) {
            updateUserBasicInfo(user, request);
        }

        if (request.getRoleCodes() != null) {
            changeUserRoles(user, request.getRoleCodes());
        }

        if (request.getDomainIds() != null) {
            assignUserDomains(user, request.getDomainIds());
        }

        return toSummaryDto(usersRepository.save(user));
    }

    // MÉTODO ATÓMICO 1 : Datos Básicos
    /**
     * Actualiza información básica del usuario: password, nombre, estado activo.
     * Acción auditada: USER_DATA_UPDATED
     */
    @Transactional
    private void updateUserBasicInfo(Users user, UpdateUserByAdminRequest request) {
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            user.setName(request.getName());
        }

        if (request.getActive() != null) {
            user.setActive(request.getActive());
        }
    }

    // MÉTODO ATÓMICO 2: Cambiar Roles
    /**
     * Cambia los roles asignados a un usuario.
     * Regla: Si pierde JEFE_DOMINIO, se desvincula de dominios.
     * Acción auditada: USER_ROLES_CHANGED
     */
    @Transactional
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

        // Remover roles no requeridos
        user.getUserRoles().removeIf(ur -> !requestedRoleIds.contains(ur.getRole().getId()));

        // Agregar nuevos roles
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

        // Regla de negocio: pierde JEFE_DOMINIO → se desvincula de dominios
        if (!newRolesIncludeJefeDominio) {
            user.getUserDomains().clear();
        }
    }

    // MÉTODO ATÓMICO 3: Asignar Dominios
    /**
     * Asigna dominios activos a un usuario JEFE_DOMINIO.
     * Requisito: usuario debe tener rol JEFE_DOMINIO.
     * Acción auditada: USER_DOMAINS_ASSIGNED
     */
    @Transactional
    private void assignUserDomains(Users user, List<UUID> domainIds) {
        boolean hasJefeDominio = user.getUserRoles().stream()
                .anyMatch(ur -> "JEFE_DOMINIO".equals(ur.getRole().getCode()));

        if (!hasJefeDominio) {
            throw new IllegalArgumentException(
                    "No se pueden asignar dominios: el usuario no tiene el rol jefe de dominio");
        }

        Set<UUID> requestedDomainIds = new HashSet<>(domainIds);

        Set<UUID> currentDomainIds = user.getUserDomains().stream()
                .map(ud -> ud.getDomain().getId())
                .collect(Collectors.toSet());

        // Remover vínculos no requeridos
        user.getUserDomains().removeIf(ud -> !requestedDomainIds.contains(ud.getDomain().getId()));

        // Agregar solo los nuevos
        for (UUID domainId : requestedDomainIds) {
            if (!currentDomainIds.contains(domainId)) {
                Domains domain = domainsRepository.findById(domainId)
                        .orElseThrow(() -> new IllegalArgumentException("Dominio no encontrado: " + domainId));

                // Validar que el dominio esté activo
                if (!Boolean.TRUE.equals(domain.getActive())) {
                    throw new IllegalArgumentException(
                            "El dominio está desactivado: " + domain.getName());
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

    // MÉTODO ATÓMICO 4: Desactivar Usuario
    //
    /**
     * Desactiva un usuario (activo = false) sin bloquearlo permanentemente.
     * Diferente de blockUser que es irreversible.
     * Acción auditada: USER_DEACTIVATED
     */
    @Transactional
    public UserSummaryDto deactivateUser(UUID userId) {
        Users adminUser = getAuthenticatedAdmin();

        if (adminUser.getId().equals(userId)) {
            throw new IllegalArgumentException("El ADMIN no puede desactivarse a sí mismo");
        }

        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + userId));

        if (Boolean.TRUE.equals(user.getBlocked())) {
            throw new IllegalStateException(
                    "No se puede desactivar un usuario que está bloqueado permanentemente");
        }

        if (!user.getActive()) {
            throw new IllegalStateException("El usuario ya está desactivado");
        }

        user.setActive(false);
        return toSummaryDto(usersRepository.save(user));
    }

    // MÉTODO ATÓMICO 5: Reactivar Usuario
    /**
     * Reactiva un usuario desactivado (activo = true).
     * No puede reactivar bloqueados permanentemente.
     * Acción auditada: USER_REACTIVATED
     */
    @Transactional
    public UserSummaryDto reactivateUser(UUID userId) {
        getAuthenticatedAdmin(); // Solo validar que sea admin

        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + userId));

        if (Boolean.TRUE.equals(user.getBlocked())) {
            throw new IllegalStateException(
                    "No se puede reactivar un usuario que está bloqueado permanentemente");
        }

        if (user.getActive()) {
            throw new IllegalStateException("El usuario ya está activo");
        }

        user.setActive(true);
        return toSummaryDto(usersRepository.save(user));
    }

    // BLOQUEO PERMANENTE solo ADMIN, irreversible desde la API
    @Transactional
    public UserSummaryDto blockUser(UUID targetUserId) {

        Users adminUser = getAuthenticatedAdmin();

        if (adminUser.getId().equals(targetUserId)) {
            throw new IllegalArgumentException("El ADMIN no puede bloquearse a sí mismo");
        }

        Users target = usersRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + targetUserId));

        if (Boolean.TRUE.equals(target.getBlocked())) {
            throw new IllegalStateException("El usuario ya está bloqueado permanentemente");
        }

        target.setBlocked(true);
        target.setActive(false); // coherencia (bloqueado siempre implica inactivo)

        return toSummaryDto(usersRepository.save(target));
    }

    // LISTAR TODOS LOS USUARIOS solo ADMIN
    @Transactional(readOnly = true)
    public List<UserSummaryDto> getAllUsers() {
        getAuthenticatedAdmin(); // valida que sea admin
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

    // MÉTODO AUXILIAR PARA VALIDAR QUE EL USUARIO AUTENTICADO ES ADMIN
    private Users getAuthenticatedAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Users user = usersRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new IllegalArgumentException("Usuario autenticado no encontrado"));

        boolean isAdmin = user.getUserRoles().stream()
                .anyMatch(ur -> "ADMIN".equals(ur.getRole().getCode()));
        if (!isAdmin) {
            throw new SecurityException("Acceso denegado: se requiere rol ADMIN");
        }
        return user;
    }

    //
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