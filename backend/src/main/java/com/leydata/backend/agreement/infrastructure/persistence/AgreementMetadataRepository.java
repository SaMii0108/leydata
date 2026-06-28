package com.leydata.backend.agreement.infrastructure.persistence;

import com.leydata.backend.entity.AgreementMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgreementMetadataRepository extends JpaRepository<AgreementMetadata, UUID> {
    Optional<AgreementMetadata> findByAgreementId(UUID agreementId);
}
