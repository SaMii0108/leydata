package com.leydata.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.io.Serializable;

@Entity
@Table(name = "users_role")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UsersRole {

    @EmbeddedId
    private UsersRoleId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private Users user;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("roleId")
    @JoinColumn(name = "role_id")
    private Role role;

    @Embeddable
    public static class UsersRoleId implements Serializable {
        private java.util.UUID userId;
        private Integer roleId;
    }
}