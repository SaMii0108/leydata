package com.leydata.backend.repository;

import com.leydata.backend.entity.SystemUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface SystemUserRepository extends JpaRepository<SystemUser, UUID> {
    Optional<SystemUser> findByCorporateEmail(String corporateEmail);
}