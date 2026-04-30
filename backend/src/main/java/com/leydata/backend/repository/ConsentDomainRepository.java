package com.leydata.backend.repository;

import com.leydata.backend.entity.ConsentDomain;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ConsentDomainRepository extends JpaRepository<ConsentDomain, Long> {
    Optional<ConsentDomain> findByDomainSlug(String domainSlug);
}