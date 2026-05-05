package com.leydata.backend.repository;

import com.leydata.backend.entity.Domains;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface DomainsRepository extends JpaRepository<Domains, UUID> {
    Optional<Domains> findByCode(String code);
}