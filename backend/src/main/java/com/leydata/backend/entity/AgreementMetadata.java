package com.leydata.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "agreement_metadata")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AgreementMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agreement_id", insertable = false, updatable = false)
    private Agreements agreement;

    @Column(name = "agreement_id", nullable = false)
    private UUID agreementId;

    @Column(name = "ip_origin")
    private InetAddress ipOrigin;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "capture_channel")
    private String captureChannel;

    @Column(name = "signature_token")
    private String signatureToken;

    @Column(name = "auth_provider")
    private String authProvider;

    @Column(name = "extra_variables", columnDefinition = "JSONB")
    private String extraVariables;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}