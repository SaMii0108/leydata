package com.leydata.backend.repository;

import com.leydata.backend.entity.TemplateDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface TemplateDetailRepository extends JpaRepository<TemplateDetail, UUID> {
    // Este es el que trae los checks ordenados para React
    List<TemplateDetail> findByTemplateIdOrderByDisplayOrderAsc(UUID templateId);
}