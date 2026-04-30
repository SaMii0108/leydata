package com.leydata.backend.repository;

import com.leydata.backend.entity.PrivacyDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PrivacyDocumentRepository extends JpaRepository<PrivacyDocument, UUID> {
}