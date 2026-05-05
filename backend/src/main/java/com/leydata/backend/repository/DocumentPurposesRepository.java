package com.leydata.backend.repository;

import com.leydata.backend.entity.DocumentPurposes;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentPurposesRepository
        extends JpaRepository<DocumentPurposes, DocumentPurposes.DocumentPurposesId> {
}