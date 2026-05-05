package com.leydata.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "privacy_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PrivacyDocuments {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "template_key")
    private String templateKey;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "url_document")
    private String urlDocument;

    @Column(name = "hash_sha256", nullable = false)
    private String hashSha256;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "publish_at")
    private LocalDateTime publishAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", insertable = false, updatable = false)
    private Users createdByUser;

    @Column(name = "created_by")
    private UUID createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by", insertable = false, updatable = false)
    private Users approvedByUser;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<DocumentPurposes> documentPurposes;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Agreements> agreements;
}