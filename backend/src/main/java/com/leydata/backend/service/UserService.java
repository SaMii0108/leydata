package com.leydata.backend.service;

import com.leydata.backend.dto.CreateUserRequest;
import com.leydata.backend.dto.UpdateAdminProfileRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.leydata.backend.entity.Role;
import com.leydata.backend.entity.Users;
import com.leydata.backend.entity.UsersRole;
import com.leydata.backend.repository.RoleRepository;
import com.leydata.backend.repository.UsersRepository;
import com.leydata.backend.repository.UsersRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UsersRepository usersRepository;
    private final RoleRepository roleRepository;
    private final UsersRoleRepository usersRoleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Users createUser(CreateUserRequest request) {
        // Validar que el usuario no exista
        if (usersRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("User already exists with email: " + request.getEmail());
        }

        // Buscar el rol
        Role role = roleRepository.findByCode(request.getRoleCode())
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + request.getRoleCode()));

        // Crear usuario
        Users user = new Users();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setName(request.getName());
        user.setActive(true);
        user.setCreatedAt(LocalDateTime.now());

        Users savedUser = usersRepository.save(user);

        // Asignar rol
        UsersRole usersRole = new UsersRole();
        UsersRole.UsersRoleId id = new UsersRole.UsersRoleId();
        id.setUserId(savedUser.getId());
        id.setRoleId(role.getId());
        usersRole.setId(id);
        usersRole.setUser(savedUser);
        usersRole.setRole(role);
        usersRoleRepository.save(usersRole);

        return savedUser;
    }

    @Transactional
    public Users updateAdminProfile(UpdateAdminProfileRequest request) {
        // Obtener el usuario autenticado
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentEmail = authentication.getName();

        Users user = usersRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        // Validar que sea ADMIN
        boolean isAdmin = user.getUserRoles().stream()
                .anyMatch(userRole -> "ADMIN".equals(userRole.getRole().getCode()));
        if (!isAdmin) {
            throw new IllegalArgumentException("Solo el ADMIN puede actualizar su perfil");
        }

        // Actualizar email si se proporciona
        if (request.getEmail() != null && !request.getEmail().isEmpty()) {
            // Validar que el email no esté en uso por otro usuario
            usersRepository.findByEmail(request.getEmail()).ifPresent(existingUser -> {
                if (!existingUser.getId().equals(user.getId())) {
                    throw new IllegalArgumentException("El email ya está en uso");
                }
            });
            user.setEmail(request.getEmail());
        }

        // Actualizar password si se proporciona
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        return usersRepository.save(user);
    }
}
