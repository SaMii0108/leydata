package com.leydata.backend.repository;

import com.leydata.backend.entity.PrivacyDocuments;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PrivacyDocumentsRepository extends JpaRepository<PrivacyDocuments, UUID> {
}