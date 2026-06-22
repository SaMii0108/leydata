package com.leydata.backend.purpose.infrastructure.persistence;

import com.leydata.backend.entity.PurposeRequests;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PurposeRequestsRepository extends JpaRepository<PurposeRequests, UUID> {
    List<PurposeRequests> findByRequesterId(UUID requesterId);
    List<PurposeRequests> findByStatus(String status);

    boolean existsByRequesterIdAndDomainIdAndTitleAndStatus(
            UUID requesterId, UUID domainId, String title, String status);
}
