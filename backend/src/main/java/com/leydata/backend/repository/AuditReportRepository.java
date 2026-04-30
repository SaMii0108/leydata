package com.leydata.backend.repository;

import com.leydata.backend.entity.AuditReport;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AuditReportRepository extends JpaRepository<AuditReport, UUID> {
}