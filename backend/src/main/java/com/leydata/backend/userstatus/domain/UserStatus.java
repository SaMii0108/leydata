package com.leydata.backend.userstatus.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_status")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserStatus {

    // keycloak_id (sub del JWT) como PK — identificador estable del usuario
    @Id
    @Column(name = "keycloak_id", nullable = false)
    private String keycloakId;

    @Column(name = "blocked", nullable = false)
    @Builder.Default
    private Boolean blocked = false;

    @Column(name = "blocked_at")
    private LocalDateTime blockedAt;
}
