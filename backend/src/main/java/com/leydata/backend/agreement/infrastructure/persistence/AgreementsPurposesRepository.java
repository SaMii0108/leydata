package com.leydata.backend.agreement.infrastructure.persistence;

import com.leydata.backend.entity.AgreementsPurposes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgreementsPurposesRepository extends JpaRepository<AgreementsPurposes, UUID> {
    List<AgreementsPurposes> findByAgreementId(UUID agreementId);
    Optional<AgreementsPurposes> findTopByOrderByCreatedAtDesc();

    /**
     * Todas las AgreementsPurposes en estado ACTIVE cuyo expiresAt ya pasó,
     * dentro de los acuerdos de un dominio específico (para pending-deletions).
     */
    @Query("""
        SELECT ap FROM AgreementsPurposes ap
        JOIN Agreements a ON a.id = ap.agreementId
        JOIN Templates t ON t.id = a.templateId
        WHERE t.domainId = :domainId
          AND ap.status = 'ACTIVE'
          AND ap.expiresAt IS NOT NULL
          AND ap.expiresAt < :now
        ORDER BY ap.expiresAt ASC
        """)
    List<AgreementsPurposes> findExpiredByDomainId(
            @Param("domainId") UUID domainId,
            @Param("now") LocalDateTime now);

    Optional<AgreementsPurposes> findByAgreementIdAndPurposeId(UUID agreementId, UUID purposeId);
}
