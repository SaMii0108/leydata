package com.leydata.backend.repository;

import com.leydata.backend.entity.TemplatePurposes;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TemplatePurposesRepository
        extends JpaRepository<TemplatePurposes, TemplatePurposes.TemplatePurposesId> {
}