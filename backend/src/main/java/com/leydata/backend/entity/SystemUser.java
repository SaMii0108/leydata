package com.leydata.backend.entity;

import java.util.UUID;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "system_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SystemUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_admin")
    private UUID idAdmin;

    @Column(name = "corporate_email", nullable = false, unique = true)
    private String corporateEmail;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "system_role", nullable = false)
    private String systemRole; // SUPERADMIN, COMPLIANCE, ROLE_AREA_MKT

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;
}
