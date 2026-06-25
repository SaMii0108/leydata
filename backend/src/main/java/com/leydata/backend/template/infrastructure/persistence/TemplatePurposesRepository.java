package com.leydata.backend.template.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.leydata.backend.entity.TemplatePurposes;

public interface TemplatePurposesRepository
        extends JpaRepository<TemplatePurposes, TemplatePurposes.TemplatePurposesId> {

                List<TemplatePurposes> findByTemplate_IdOrderByOrderPosition(UUID templateId);
                Optional<TemplatePurposes> findByTemplate_IdAndPurpose_Id(UUID templateId, UUID purposeId);
                boolean existsByTemplate_IdAndOrderPosition(UUID templateId, Integer orderPosition);
                boolean existsByTemplate_IdAndIsVisibleTrue(UUID templateId);    
                void deleteByTemplate_IdAndPurpose_Id(UUID templateId, UUID purposeId);

}