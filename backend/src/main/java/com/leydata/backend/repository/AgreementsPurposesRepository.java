package com.leydata.backend.repository;

import com.leydata.backend.entity.AgreementsPurposes;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AgreementsPurposesRepository extends JpaRepository<AgreementsPurposes, UUID> {
}