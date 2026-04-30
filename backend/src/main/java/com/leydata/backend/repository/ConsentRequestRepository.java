package com.leydata.backend.repository;

import com.leydata.backend.entity.ConsentRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ConsentRequestRepository extends JpaRepository<ConsentRequest, UUID> {
}