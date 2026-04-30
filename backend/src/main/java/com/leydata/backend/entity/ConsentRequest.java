package com.leydata.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "consent_request")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConsentRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Quién lo pide (El usuario de Área)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private SystemUser requester;

    // A qué área pertenece esta solicitud
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "domain_id", nullable = false)
    private ConsentDomain domain;

    @Column(name = "request_title", nullable = false)
    private String requestTitle; // Ej: "Nuevo canal de WhatsApp"

    @Column(name = "request_description", columnDefinition = "TEXT", nullable = false)
    private String requestDescription;

    @Column(name = "request_status", nullable = false)
    private String requestStatus; // Guardará: PENDING, APPROVED, REJECTED

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt; // Se llena cuando el Admin aprueba o rechaza
}