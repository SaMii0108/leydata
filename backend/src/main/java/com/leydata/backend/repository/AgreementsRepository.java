package com.leydata.backend.repository;

import com.leydata.backend.entity.Agreements;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AgreementsRepository extends JpaRepository<Agreements, UUID> {
}