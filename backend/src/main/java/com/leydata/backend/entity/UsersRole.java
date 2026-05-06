package com.leydata.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "users_role")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UsersRole {

    @EmbeddedId
    private UsersRoleId id = new UsersRoleId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private Users user;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("roleId")
    @JoinColumn(name = "role_id")
    private Role role;

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class UsersRoleId implements Serializable {

        @Column(name = "user_id")
        private UUID userId;

        @Column(name = "role_id")
        private UUID roleId;
    }
}