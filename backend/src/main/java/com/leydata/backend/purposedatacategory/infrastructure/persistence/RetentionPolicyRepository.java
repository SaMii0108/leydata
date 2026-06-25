package com.leydata.backend.purposedatacategory.infrastructure.persistence;

import com.leydata.backend.entity.DataRetentionPolicies;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RetentionPolicyRepository extends JpaRepository<DataRetentionPolicies, UUID> {

    Optional<DataRetentionPolicies> findByPurposeDataCategoryId(UUID purposeDataCategoryId);
}
