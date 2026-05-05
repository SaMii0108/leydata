package com.leydata.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.io.Serializable;

@Entity
@Table(name = "user_domains")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserDomains {

    @EmbeddedId
    private UserDomainsId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private Users user;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("domainId")
    @JoinColumn(name = "domain_id")
    private Domains domain;

    @Embeddable
    public static class UserDomainsId implements Serializable {
        private java.util.UUID userId;
        private java.util.UUID domainId;
    }
}