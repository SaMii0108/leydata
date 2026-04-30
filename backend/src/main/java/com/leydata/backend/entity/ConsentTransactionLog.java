package com.leydata.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "consent_transaction_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConsentTransactionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pseudo_id", nullable = false)
    private PseudoMap pseudoMap;

    // Que consentimiento ha sido modificado o dado
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consent_config_id", nullable = false)
    private ConsentConfig consentConfig;

    // Si rechazaron o aceptaron el consentimiento
    @Column(name = "action_taken", nullable = false)
    private String actionTaken; // GRANTED, REVOKED

    // Desde donde se hizo clic
    // Si hay 1.000 aceptaciones desde la misma IP en 1 minuto, el Agente de
    // Auditoría puede detectar un ataque de bots
    @Column(name = "ip_address", length = 45) // Soporta IPv4 e IPv6
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent; // Navegador y Sistema Operativo completo

    @Column(name = "device_type")
    private String deviceType; // Ej: Mobile, Desktop, Tablet

    // Que version de la app hizo la transaccion por si la app o web tenia un error
    // ayuda a identificarlo
    @Column(name = "application_version")
    private String applicationVersion;

    // Cuando se toco el consentimiento es inmutable
    @Column(name = "timestamp", nullable = false, updatable = false)
    private LocalDateTime timestamp;

    // Firma del consentimiento para validar la integridad con el Agente de python
    @Column(name = "evidence_signature", nullable = false, updatable = false, length = 64)
    private String evidenceSignature;
}