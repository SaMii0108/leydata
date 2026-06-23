package com.leydata.backend.user.infrastructure.persistence;

import com.leydata.backend.entity.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsersRepository extends JpaRepository<Users, UUID> {
    Optional<Users> findByEmail(String email);
    Optional<Users> findByKeycloakId(String keycloakId);

    @Query("""
            SELECT DISTINCT u FROM Users u
            JOIN u.userDomains ud
            JOIN u.userRoles ur
            JOIN ur.role r
            WHERE ud.domain.id = :domainId
              AND r.code = 'JEFE_DOMINIO'
              AND u.active = true
            """)
    List<Users> findActiveJefesByDomainId(@Param("domainId") UUID domainId);
}
