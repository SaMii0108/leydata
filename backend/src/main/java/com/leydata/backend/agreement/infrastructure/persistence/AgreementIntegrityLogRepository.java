package com.leydata.backend.agreement.infrastructure.persistence;

import com.leydata.backend.entity.AgreementIntegrityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgreementIntegrityLogRepository extends JpaRepository<AgreementIntegrityLog, UUID> {
    List<AgreementIntegrityLog> findByAgreementIdOrderByCreatedAtDesc(UUID agreementId);
    List<AgreementIntegrityLog> findByIsValidFalse();
    Optional<AgreementIntegrityLog> findTopByOrderByCreatedAtDesc();
}
