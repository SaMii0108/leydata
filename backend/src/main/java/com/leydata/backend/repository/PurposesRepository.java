package com.leydata.backend.repository;

import com.leydata.backend.entity.Purposes;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PurposesRepository extends JpaRepository<Purposes, UUID> {
}