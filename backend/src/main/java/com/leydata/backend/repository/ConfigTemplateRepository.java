package com.leydata.backend.repository;

import com.leydata.backend.entity.ConfigTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ConfigTemplateRepository extends JpaRepository<ConfigTemplate, UUID> {
}