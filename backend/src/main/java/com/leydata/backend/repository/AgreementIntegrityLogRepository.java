package com.leydata.backend.repository;

import com.leydata.backend.entity.AgreementIntegrityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AgreementIntegrityLogRepository extends JpaRepository<AgreementIntegrityLog, UUID> {
}