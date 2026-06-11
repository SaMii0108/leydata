package com.leydata.backend.domain;

import com.leydata.backend.entity.UserDomains;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserDomainsRepository extends JpaRepository<UserDomains, UserDomains.UserDomainsId> {
}