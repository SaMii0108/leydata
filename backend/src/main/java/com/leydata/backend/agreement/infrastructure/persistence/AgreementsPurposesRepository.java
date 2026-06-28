package com.leydata.backend.agreement.infrastructure.persistence;

import com.leydata.backend.entity.AgreementsPurposes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgreementsPurposesRepository extends JpaRepository<AgreementsPurposes, UUID> {
    List<AgreementsPurposes> findByAgreementId(UUID agreementId);
    Optional<AgreementsPurposes> findTopByOrderByCreatedAtDesc();
}
