package com.leydata.backend.repository;

import com.leydata.backend.entity.LegalBasisCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface LegalBasisCatalogRepository extends JpaRepository<LegalBasisCatalog, UUID> {
}