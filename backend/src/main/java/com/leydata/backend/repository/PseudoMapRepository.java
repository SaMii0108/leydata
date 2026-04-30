package com.leydata.backend.repository;

import com.leydata.backend.entity.PseudoMap;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PseudoMapRepository extends JpaRepository<PseudoMap, String> {
    Optional<PseudoMap> findByPseudoId(String pseudoId);
}