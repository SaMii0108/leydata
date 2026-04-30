package com.leydata.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "privacy_document")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PrivacyDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private SystemUser author;

    // Texto legal del documento Text por si es muy largo updatable = false para que
    // no se pueda editar
    @Column(name = "legal_content", columnDefinition = "TEXT", nullable = false, updatable = false)
    private String legalContent;

    // Hash SHA256 del texto legal para que cuando se audite si el hash ha cambiado
    @Column(name = "sha256_hash", nullable = false, updatable = false)
    private String sha256Hash;

    // Version del documento
    @Column(name = "document_version", nullable = false)
    private Integer documentVersion;

    // Estado del documento
    @Column(name = "document_status", nullable = false)
    private String documentStatus; // ACTIVE, ARCHIVED

    // Fecha de creacion
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}