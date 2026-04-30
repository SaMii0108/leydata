package com.leydata.backend.repository;

import com.leydata.backend.entity.CurrentConsentState;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CurrentConsentStateRepository extends JpaRepository<CurrentConsentState, UUID> {
    List<CurrentConsentState> findByPseudoMap_PseudoId(String pseudoId);
}