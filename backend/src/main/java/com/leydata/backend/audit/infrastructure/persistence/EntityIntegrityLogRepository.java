package com.leydata.backend.audit.infrastructure.persistence;

import com.leydata.backend.entity.EntityIntegrityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EntityIntegrityLogRepository extends JpaRepository<EntityIntegrityLog, UUID> {
    List<EntityIntegrityLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, UUID entityId);
    List<EntityIntegrityLog> findByIsValidFalse();
    List<EntityIntegrityLog> findByIsValidFalseAndEntityType(String entityType);
    Optional<EntityIntegrityLog> findTopByOrderByCreatedAtDesc();
}
