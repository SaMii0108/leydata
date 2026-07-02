package com.leydata.backend.privacydoc.infrastructure.persistence;

import com.leydata.backend.entity.DocumentTemplates;
import com.leydata.backend.privacydoc.domain.enums.DocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio JPA para la tabla de unión document_templates.
 * Módulo: privacydoc — capa infrastructure/persistence.
 */
@Repository
public interface DocumentTemplatesRepository
        extends JpaRepository<DocumentTemplates, DocumentTemplates.DocumentTemplatesId> {

    /** Solo templates activamente vinculados al documento. */
    List<DocumentTemplates> findByDocument_IdAndIsActiveTrue(UUID documentId);

    /** Busca el vínculo independientemente del estado (para soft-delete y reactivación). */
    Optional<DocumentTemplates> findByDocument_IdAndTemplate_Id(UUID documentId, UUID templateId);

    boolean existsByDocument_IdAndTemplate_IdAndIsActiveTrue(UUID documentId, UUID templateId);

    /**
     * Documentos PUBLISHED activos que comparten un template dado — usado para archivar
     * automáticamente cualquier versión anterior al publicar (a lo sumo un PUBLISHED por template).
     */
    List<DocumentTemplates> findByTemplate_IdAndIsActiveTrueAndDocument_StatusAndDocument_IsActiveTrue(
            UUID templateId, DocumentStatus status);
}
