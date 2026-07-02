package com.leydata.backend.agreement.infrastructure.persistence;

import com.leydata.backend.entity.Agreements;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /** Acuerdo activo buscado por identificador opaco del titular + templateId (para el Orquestador B2B). */
    @Query("""
        SELECT a FROM Agreements a
        JOIN DataSubjects ds ON ds.id = a.dataSubjectId
        WHERE ds.identifier = :subjectIdentifier
          AND a.templateId = :templateId
          AND a.status = 'ACTIVE'
        """)
    Optional<Agreements> findActiveBySubjectIdentifierAndTemplateId(
            @Param("subjectIdentifier") String subjectIdentifier,
            @Param("templateId") UUID templateId);

    /** Todos los acuerdos ACTIVE de un titular para templates de un dominio específico. */
    @Query("""
        SELECT a FROM Agreements a
        JOIN DataSubjects ds ON ds.id = a.dataSubjectId
        JOIN Templates t ON t.id = a.templateId
        WHERE ds.identifier = :subjectIdentifier
          AND t.domainId = :domainId
          AND a.status = 'ACTIVE'
        ORDER BY a.createdAt DESC
        """)
    List<Agreements> findActiveBySubjectIdentifierAndDomainId(
            @Param("subjectIdentifier") String subjectIdentifier,
            @Param("domainId") UUID domainId);
}
