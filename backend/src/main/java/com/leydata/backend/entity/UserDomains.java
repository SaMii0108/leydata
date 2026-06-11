package com.leydata.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "user_domains")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserDomains {

    @EmbeddedId
    private UserDomainsId id = new UserDomainsId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private Users user;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("domainId")
    @JoinColumn(name = "domain_id")
    private Domains domain;

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class UserDomainsId implements Serializable {
        private UUID userId;
        private UUID domainId;
    }
}