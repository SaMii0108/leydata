package com.leydata.backend.repository;

import com.leydata.backend.entity.Templates;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface TemplatesRepository extends JpaRepository<Templates, UUID> {
}