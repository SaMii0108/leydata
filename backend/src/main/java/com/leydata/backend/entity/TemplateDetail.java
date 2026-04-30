package com.leydata.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "template_detail")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TemplateDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private ConfigTemplate template;

    // Vínculo a la configuración (el checkbox)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consent_config_id", nullable = false)
    private ConsentConfig consentConfig;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder; // Ej: 1 para Términos, 2 para Marketing, etc.
}