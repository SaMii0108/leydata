package com.leydata.backend.repository;

import com.leydata.backend.entity.UsersRole;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsersRoleRepository extends JpaRepository<UsersRole, UsersRole.UsersRoleId> {
}