package com.leydata.backend.purposes.infrastructure.persistence;

import com.leydata.backend.entity.Purposes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurposesRepository extends JpaRepository<Purposes, UUID> {
    boolean existsByCode(String code);
    List<Purposes> findByIsActiveTrue();
    List<Purposes> findByDomainIdAndIsActiveTrue(UUID domainId);
    Optional<Purposes> findByIdAndDomainId(UUID id, UUID domainId);
}
