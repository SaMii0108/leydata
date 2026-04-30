package com.leydata.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "config_template")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConfigTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "template_name", nullable = false)
    private String templateName; // Ej: "Registro Mobile", "Panel de Preferencias Web"

    @Column(name = "template_version", nullable = false)
    private Integer templateVersion;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;
}