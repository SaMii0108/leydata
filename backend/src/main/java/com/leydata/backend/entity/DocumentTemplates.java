package com.leydata.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.io.Serializable;

@Entity
@Table(name = "document_templates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DocumentTemplates {

    @EmbeddedId
    private DocumentTemplatesId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("documentId")
    @JoinColumn(name = "document_id")
    private PrivacyDocuments document;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("templateId")
    @JoinColumn(name = "template_id")
    private Templates template;

    // Soft delete: false = desvinculada pero conservada para auditoría
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class DocumentTemplatesId implements Serializable {
        private java.util.UUID documentId;
        private java.util.UUID templateId;
    }
}
