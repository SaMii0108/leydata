package com.leydata.backend.privacydoc.infrastructure.persistence;

import com.leydata.backend.entity.DocumentPurposes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio JPA para la tabla de unión document_purposes.
 * Módulo: privacydoc — capa infrastructure/persistence.
 */
@Repository
public interface DocumentPurposesRepository
        extends JpaRepository<DocumentPurposes, DocumentPurposes.DocumentPurposesId> {

    /** Solo finalidades activas del documento. */
    List<DocumentPurposes> findByDocument_IdAndIsActiveTrue(UUID documentId);

    /** Busca el vínculo independientemente del estado (para soft-delete y reactivación). */
    Optional<DocumentPurposes> findByDocument_IdAndPurpose_Id(UUID documentId, UUID purposeId);

    boolean existsByDocument_IdAndPurpose_IdAndIsActiveTrue(UUID documentId, UUID purposeId);

    boolean existsByDocument_IdAndIsActiveTrue(UUID documentId);
}
