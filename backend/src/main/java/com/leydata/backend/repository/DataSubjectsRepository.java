package com.leydata.backend.repository;

import com.leydata.backend.entity.DataSubjects;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface DataSubjectsRepository extends JpaRepository<DataSubjects, UUID> {
}