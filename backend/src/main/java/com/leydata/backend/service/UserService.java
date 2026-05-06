package com.leydata.backend.service;

import com.leydata.backend.dto.CreateUserRequest;
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
}
