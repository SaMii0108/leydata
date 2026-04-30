package com.leydata.backend.repository;

import com.leydata.backend.entity.ConsentConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ConsentConfigRepository extends JpaRepository<ConsentConfig, UUID> {
    Optional<ConsentConfig> findByTechnicalSlug(String technicalSlug);
}