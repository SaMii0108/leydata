package com.leydata.backend.template.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateTemplateRequest {
    @NotBlank
    private String templateKey;
    @NotBlank
    private String name;
    private String description;
    private String title;
}
