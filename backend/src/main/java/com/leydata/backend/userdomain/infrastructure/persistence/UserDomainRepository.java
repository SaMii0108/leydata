package com.leydata.backend.userdomain.infrastructure.persistence;

import com.leydata.backend.userdomain.domain.UserDomain;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserDomainRepository extends JpaRepository<UserDomain, UUID> {
    List<UserDomain> findByKeycloakId(String keycloakId);
    List<UserDomain> findByDomain_Id(UUID domainId);
    void deleteByKeycloakId(String keycloakId);
    void deleteByKeycloakIdAndDomainId(String keycloakId, UUID domainId);
    boolean existsByKeycloakIdAndDomainId(String keycloakId, UUID domainId);
}
