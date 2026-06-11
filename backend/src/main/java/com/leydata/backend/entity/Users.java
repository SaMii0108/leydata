package com.leydata.backend.entity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

//Entidad local de usuarios del sistema.
//Con Keycloak, la autenticación (password, sesión, MFA) la gestiona Keycloak.
//Esta entidad existe para la lógica de negocio: estado activo/bloqueado,
//asignación de dominios y roles dentro de nuestra aplicación.
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Users {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    //ID único del usuario en Keycloak (claim "sub" del JWT).
    //Es el identificador estable: no cambia aunque el usuario actualice su email.
    //Se usa como puente principal entre Keycloak y nuestra BD local.
    //Nullable en la creación inicial: se auto-completa en el primer login del usuario.
    @Column(name = "keycloak_id", unique = true)
    private String keycloakId;

    //Email del usuario. Ya no es el puente principal con Keycloak (lo es keycloak_id),
    //pero sigue siendo único y útil para búsquedas y notificaciones.
    @Column(name = "email", nullable = false, unique = true)
    private String email;

    //Nullable: Keycloak gestiona las credenciales, no nuestro backend
    @Column(name = "password")
    private String password;

    @Column(name = "name", nullable = false)
    private String name;

    //false = desactivado temporalmente por el admin (reversible)
    @Column(name = "active", nullable = false)
    private Boolean active;

    //true = bloqueado permanentemente por el admin (irreversible desde la API)
    @Column(name = "blocked", nullable = false)
    private Boolean blocked = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    //Indica si el admin requiere que el usuario complete una acción en Keycloak
    //(ej: cambio de contraseña obligatorio en el próximo login)
    @Column(name = "must_change_password", nullable = false)
    private Boolean mustChangePassword = false;

    //Roles asignados en nuestro sistema (sincronizados con los realm roles de Keycloak)
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<UsersRole> userRoles;

    //Dominios asignados: solo aplica a usuarios con rol JEFE_DOMINIO
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<UserDomains> userDomains;

    @OneToMany(mappedBy = "requester", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PurposeRequests> requestedPurposes;

    @OneToMany(mappedBy = "reviewer", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PurposeRequests> reviewedPurposes;

    @OneToMany(mappedBy = "createdBy", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Purposes> createdPurposes;

    @OneToMany(mappedBy = "approvedBy", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Purposes> approvedPurposes;

    @OneToMany(mappedBy = "createdBy", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PrivacyDocuments> createdDocuments;

    @OneToMany(mappedBy = "approvedBy", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PrivacyDocuments> approvedDocuments;
}
