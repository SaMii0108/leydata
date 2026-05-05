package com.leydata.backend.repository;

import com.leydata.backend.entity.PurposeDataCategories;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PurposeDataCategoriesRepository extends JpaRepository<PurposeDataCategories, UUID> {
}