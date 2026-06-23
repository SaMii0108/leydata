package com.leydata.backend.datacategory.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DataCategoryRequest {

    @NotBlank
    private String code;

    @NotBlank
    private String name;

    private String description;

    @NotNull
    private Boolean isSensitive;
}
