package com.leydata.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "template_purposes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TemplatePurposes {

    @EmbeddedId
    private TemplatePurposesId id = new TemplatePurposesId();

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
    private Boolean isVisible = true;

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class TemplatePurposesId implements Serializable {
        private UUID templateId;
        private UUID purposeId;
    }
}