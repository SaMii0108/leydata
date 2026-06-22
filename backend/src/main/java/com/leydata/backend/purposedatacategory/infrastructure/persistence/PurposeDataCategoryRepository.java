package com.leydata.backend.purposedatacategory.infrastructure.persistence;

import com.leydata.backend.entity.PurposeDataCategories;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurposeDataCategoryRepository extends JpaRepository<PurposeDataCategories, UUID> {

    List<PurposeDataCategories> findByPurposeId(UUID purposeId);

    Optional<PurposeDataCategories> findByPurposeIdAndDataCategoryId(UUID purposeId, UUID dataCategoryId);

    boolean existsByPurposeIdAndDataCategoryId(UUID purposeId, UUID dataCategoryId);

    boolean existsByDataCategoryId(UUID dataCategoryId);
}
