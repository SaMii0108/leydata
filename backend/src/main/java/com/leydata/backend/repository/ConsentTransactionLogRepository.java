package com.leydata.backend.repository;

import com.leydata.backend.entity.ConsentTransactionLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentTransactionLogRepository extends JpaRepository<ConsentTransactionLog, Long> {
}