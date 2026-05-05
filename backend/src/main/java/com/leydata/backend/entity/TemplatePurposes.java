package com.leydata.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.io.Serializable;

@Entity
@Table(name = "template_purposes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TemplatePurposes {

    @EmbeddedId
    private TemplatePurposesId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("templateId")
    @JoinColumn(name = "template_id")
    private Templates template;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("purposeId")
    @JoinColumn(name = "purpose_id")
    private Purposes purpose;

    @Column(name = "order_position")
    private Integer orderPosition;

    @Column(name = "is_visible", nullable = false)
    private Boolean isVisible;

    @Embeddable
    public static class TemplatePurposesId implements Serializable {
        private java.util.UUID templateId;
        private java.util.UUID purposeId;
    }
}