package com.leydata.backend.userstatus.infrastructure.persistence;

import com.leydata.backend.userstatus.domain.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserStatusRepository extends JpaRepository<UserStatus, String> {
}
