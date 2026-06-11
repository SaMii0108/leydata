package com.leydata.backend.seeder;

import com.leydata.backend.entity.Role;
import com.leydata.backend.entity.Users;
import com.leydata.backend.entity.UsersRole;
import com.leydata.backend.user.RoleRepository;
import com.leydata.backend.user.UsersRepository;
import com.leydata.backend.user.UsersRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

//Inicializa los datos mínimos requeridos para el funcionamiento del sistema.
//Solo crea registros locales en nuestra BD; los usuarios deben crearse también en Keycloak
//con el mismo email para poder autenticarse.
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UsersRepository usersRepository;
    private final RoleRepository roleRepository;
    private final UsersRoleRepository usersRoleRepository;

    @Override
    public void run(String... args) {
        seedRoles();
        seedAdminUser();
    }

    //Crea los roles base del sistema si no existen
    private void seedRoles() {
        List<String> requiredRoles = List.of("ADMIN", "DPO", "JEFE_DOMINIO");
        requiredRoles.forEach(code -> roleRepository.findByCode(code)
                .orElseGet(() -> {
                    log.info("Creando rol base: {}", code);
                    return roleRepository.save(createRole(code));
                }));
    }

    private Role createRole(String code) {
        Role role = new Role();
        role.setCode(code);
        role.setName(code);
        return role;
    }

    //Crea el usuario ADMIN inicial en nuestra BD local.
    //IMPORTANTE: debe crearse el mismo usuario en Keycloak con email "admin@leydata.cl"
    //y asignarle el realm role "ADMIN" para que pueda autenticarse y acceder al sistema.
    private void seedAdminUser() {
        String adminEmail = "admin@leydata.cl";
        if (usersRepository.findByEmail(adminEmail).isPresent()) {
            return;
        }

        log.info("Creando usuario ADMIN inicial en BD local: {}", adminEmail);

        Users admin = new Users();
        admin.setEmail(adminEmail);
        //La contraseña es null: Keycloak gestiona las credenciales de acceso
        admin.setPassword(null);
        admin.setName("Administrador");
        admin.setActive(true);
        admin.setBlocked(false);
        admin.setMustChangePassword(false);
        admin.setCreatedAt(LocalDateTime.now());
        admin = usersRepository.save(admin);

        Role adminRole = roleRepository.findByCode("ADMIN")
                .orElseThrow(() -> new IllegalStateException("El rol ADMIN debe existir al momento de crear el usuario"));

        UsersRole.UsersRoleId id = new UsersRole.UsersRoleId();
        id.setUserId(admin.getId());
        id.setRoleId(adminRole.getId());

        UsersRole usersRole = new UsersRole();
        usersRole.setId(id);
        usersRole.setUser(admin);
        usersRole.setRole(adminRole);
        usersRoleRepository.save(usersRole);

        log.info("Usuario ADMIN creado en BD local. Recuerde crearlo también en Keycloak realm 'leydata'.");
    }
}
