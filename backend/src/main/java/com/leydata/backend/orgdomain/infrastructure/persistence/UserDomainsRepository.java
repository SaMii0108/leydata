package com.leydata.backend.orgdomain.infrastructure.persistence;

import com.leydata.backend.entity.UserDomains;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserDomainsRepository extends JpaRepository<UserDomains, UserDomains.UserDomainsId> {
}
