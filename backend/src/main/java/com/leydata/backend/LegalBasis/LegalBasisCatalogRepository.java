package com.leydata.backend.LegalBasis;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.leydata.backend.entity.LegalBasisCatalog;

public interface LegalBasisCatalogRepository extends JpaRepository<LegalBasisCatalog, UUID> {
    Optional<LegalBasisCatalog> findByCode(String code);
    List<LegalBasisCatalog> findByIsActiveTrue();
}