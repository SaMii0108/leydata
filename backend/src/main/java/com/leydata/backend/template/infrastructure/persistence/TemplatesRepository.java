package com.leydata.backend.template.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.leydata.backend.entity.Templates;

public interface TemplatesRepository extends JpaRepository<Templates, UUID>, JpaSpecificationExecutor<Templates> {
    Optional<Templates> findByTemplateKeyAndIsActiveTrue(String templateKey);
    List<Templates> findByTemplateKeyOrderByVersionDesc(String templateKey);
    boolean existsByTemplateKeyAndIsActiveTrue(String templateKey);
    boolean existsByTemplateKeyAndVersion(String templateKey, Integer version);
}