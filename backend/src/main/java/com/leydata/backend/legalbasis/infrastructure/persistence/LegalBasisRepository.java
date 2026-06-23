package com.leydata.backend.legalbasis.infrastructure.persistence;

import com.leydata.backend.entity.LegalBasisCatalog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LegalBasisRepository extends JpaRepository<LegalBasisCatalog, UUID> {

    Optional<LegalBasisCatalog> findByCode(String code);

    List<LegalBasisCatalog> findByIsActiveTrue();

    List<LegalBasisCatalog> findByConsentRequiredAndIsActiveTrue(Boolean consentRequired);
}
