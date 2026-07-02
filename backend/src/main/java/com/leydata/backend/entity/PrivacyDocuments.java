package com.leydata.backend.entity;

import com.leydata.backend.privacydoc.domain.enums.DocumentCategory;
import com.leydata.backend.privacydoc.domain.enums.DocumentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "privacy_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrivacyDocuments {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Agrupa todas las versiones del mismo documento lógico.
     * Al crear un documento: family_id = id propio (auto-asignado tras el primer save).
     * Al crear new-version: family_id = family_id del documento origen.
     */
    @Column(name = "document_family_id")
    private UUID documentFamilyId;

    // ── Identificación ──────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private DocumentCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private DocumentStatus status = DocumentStatus.DRAFT;

    /** Versión del documento — incrementa con cada edición. */
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;

    // ── Contenido ───────────────────────────────────────────────────────────────

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /** Texto legal editable. Fuente para la generación del PDF al publicar. */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    // ── Flujo de revisión ───────────────────────────────────────────────────────

    /** Motivo de rechazo del DPO. Obligatorio si status = REJECTED. */
    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    // ── Artefacto publicado ─────────────────────────────────────────────────────

    /**
     * PDF binario generado al publicar. Almacenado como bytea en PostgreSQL.
     * No se incluye en respuestas JSON; se sirve vía GET /api/privacy-documents/{id}/pdf.
     */
    @Column(name = "pdf_content")
    private byte[] pdfContent;

    /** SHA-256 del PDF binario. Permite verificar integridad del documento publicado. */
    @Column(name = "hash_sha256", length = 64)
    private String hashSha256;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "publish_at")
    private LocalDateTime publishAt;

    // ── Auditoría ───────────────────────────────────────────────────────────────

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_by_name")
    private String createdByName;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "approved_by_name")
    private String approvedByName;

    // ── Relaciones ───────────────────────────────────────────────────────────────

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<DocumentPurposes> documentPurposes = new ArrayList<>();

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<DocumentTemplates> documentTemplates = new ArrayList<>();

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Agreements> agreements;

    // ── Lifecycle ────────────────────────────────────────────────────────────────

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
