package com.leydata.backend.agreement.infrastructure.persistence;

import com.leydata.backend.entity.Agreements;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgreementsRepository extends JpaRepository<Agreements, UUID> {
    List<Agreements> findByDataSubjectIdOrderByCreatedAtDesc(UUID dataSubjectId);
    List<Agreements> findByTemplateId(UUID templateId);
    List<Agreements> findByStatus(String status);
    Optional<Agreements> findByDataSubjectIdAndTemplateIdAndStatus(UUID dataSubjectId, UUID templateId, String status);
    boolean existsByDataSubjectIdAndTemplateIdAndStatus(UUID dataSubjectId, UUID templateId, String status);
    boolean existsByTemplateIdIn(Collection<UUID> templateIds);
    Optional<Agreements> findTopByOrderByCreatedAtDesc();
}
