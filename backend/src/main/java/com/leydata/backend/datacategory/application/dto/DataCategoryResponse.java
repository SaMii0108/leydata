package com.leydata.backend.datacategory.application.dto;

import com.leydata.backend.entity.DataCategories;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class DataCategoryResponse {

    private UUID id;
    private String code;
    private String name;
    private String description;
    private Boolean isSensitive;
    private Boolean isSystem;
    private Boolean isActive;
    private LocalDateTime createdAt;

    public static DataCategoryResponse from(DataCategories e) {
        return DataCategoryResponse.builder()
                .id(e.getId())
                .code(e.getCode())
                .name(e.getName())
                .description(e.getDescription())
                .isSensitive(e.getIsSensitive())
                .isSystem(e.getIsSystem())
                .isActive(e.getIsActive())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
