package com.leydata.backend.seeder;

import com.leydata.backend.entity.Role;
import com.leydata.backend.entity.Users;
import com.leydata.backend.entity.UsersRole;
import com.leydata.backend.repository.RoleRepository;
import com.leydata.backend.repository.UsersRepository;
import com.leydata.backend.repository.UsersRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UsersRepository usersRepository;
    private final RoleRepository roleRepository;
    private final UsersRoleRepository usersRoleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        seedRoles();
        seedAdminUser();
    }

    private void seedRoles() {
        List<String> requiredRoles = List.of("ADMIN", "DPO", "JEFE_DOMINIO");
        requiredRoles.forEach(code -> roleRepository.findByCode(code)
                .orElseGet(() -> roleRepository.save(createRole(code))));
    }

    private Role createRole(String code) {
        Role role = new Role();
        role.setCode(code);
        role.setName(code);
        return role;
    }

    private void seedAdminUser() {
        String adminEmail = "admin@gmail.com";
        if (usersRepository.findByEmail(adminEmail).isPresent()) {
            return;
        }

        Users admin = new Users();
        admin.setEmail(adminEmail);
        admin.setPassword(passwordEncoder.encode("admin"));
        admin.setName("Administrador");
        admin.setActive(true);
        admin.setBlocked(false);
        admin.setCreatedAt(LocalDateTime.now());
        admin = usersRepository.save(admin);

        Role adminRole = roleRepository.findByCode("ADMIN")
                .orElseThrow(() -> new IllegalStateException("ADMIN role must exist"));

        UsersRole.UsersRoleId id = new UsersRole.UsersRoleId();
        id.setUserId(admin.getId());
        id.setRoleId(adminRole.getId());

        UsersRole usersRole = new UsersRole();
        usersRole.setId(id);
        usersRole.setUser(admin);
        usersRole.setRole(adminRole);
        usersRoleRepository.save(usersRole);
    }
}
