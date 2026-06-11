package com.leydata.backend.user;

import com.leydata.backend.entity.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UsersRepository extends JpaRepository<Users, UUID> {
    Optional<Users> findByEmail(String email);
    //Búsqueda por keycloak_id (claim "sub" del JWT) — identificador estable aunque cambie el email
    Optional<Users> findByKeycloakId(String keycloakId);
}