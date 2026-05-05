package com.leydata.backend.repository;

import com.leydata.backend.entity.DataRetentionPolicies;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface DataRetentionPoliciesRepository extends JpaRepository<DataRetentionPolicies, UUID> {
}