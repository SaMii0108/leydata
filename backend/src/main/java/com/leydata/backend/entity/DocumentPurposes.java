package com.leydata.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.io.Serializable;

@Entity
@Table(name = "document_purposes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DocumentPurposes {

    @EmbeddedId
    private DocumentPurposesId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("documentId")
    @JoinColumn(name = "document_id")
    private PrivacyDocuments document;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("purposeId")
    @JoinColumn(name = "purpose_id")
    private Purposes purpose;

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class DocumentPurposesId implements Serializable {
        private java.util.UUID documentId;
        private java.util.UUID purposeId;
    }
}