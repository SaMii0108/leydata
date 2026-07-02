package com.leydata.backend.privacydoc.infrastructure.persistence;

import com.leydata.backend.entity.PrivacyDocuments;
import com.leydata.backend.privacydoc.domain.enums.DocumentCategory;
import com.leydata.backend.privacydoc.domain.enums.DocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PrivacyDocumentsRepository extends JpaRepository<PrivacyDocuments, UUID> {

    /** Listado con filtros opcionales (null = sin filtro). Solo devuelve documentos activos. */
    @Query("""
            SELECT d FROM PrivacyDocuments d
            WHERE (:category IS NULL OR d.category = :category)
              AND (:status   IS NULL OR d.status   = :status)
              AND d.isActive = true
            ORDER BY d.createdAt DESC
            """)
    List<PrivacyDocuments> findByFilters(
            @Param("category") DocumentCategory category,
            @Param("status") DocumentStatus status
    );

    /**
     * Versión canónica por categoría: la de mayor número de versión entre las PUBLISHED activas.
     * Múltiples versiones de la misma categoría pueden estar publicadas simultáneamente.
     */
    Optional<PrivacyDocuments> findTopByCategoryAndStatusAndIsActiveTrueOrderByVersionDesc(
            DocumentCategory category, DocumentStatus status
    );

    /** Todas las versiones activas de una familia, ordenadas por versión descendente. */
    List<PrivacyDocuments> findByDocumentFamilyIdAndIsActiveTrueOrderByVersionDesc(UUID documentFamilyId);

    /** Comprueba si ya existe un borrador activo en la misma familia. */
    boolean existsByDocumentFamilyIdAndStatusAndIsActiveTrue(UUID documentFamilyId, DocumentStatus status);
}
