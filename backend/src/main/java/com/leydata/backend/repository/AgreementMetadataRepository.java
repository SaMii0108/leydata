package com.leydata.backend.repository;

import com.leydata.backend.entity.AgreementMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AgreementMetadataRepository extends JpaRepository<AgreementMetadata, UUID> {
}