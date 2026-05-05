package com.leydata.backend.repository;

import com.leydata.backend.entity.DataCategories;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface DataCategoriesRepository extends JpaRepository<DataCategories, UUID> {
}