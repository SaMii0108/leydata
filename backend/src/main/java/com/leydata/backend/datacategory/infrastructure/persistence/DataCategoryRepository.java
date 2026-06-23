package com.leydata.backend.datacategory.infrastructure.persistence;

import com.leydata.backend.entity.DataCategories;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DataCategoryRepository extends JpaRepository<DataCategories, UUID> {

    Optional<DataCategories> findByCode(String code);

    boolean existsByCode(String code);

    List<DataCategories> findByIsActiveTrue();

    List<DataCategories> findByIsSensitiveAndIsActiveTrue(Boolean isSensitive);
}
