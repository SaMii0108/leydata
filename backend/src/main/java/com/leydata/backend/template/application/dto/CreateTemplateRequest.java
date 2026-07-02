package com.leydata.backend.template.application.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateTemplateRequest {
    @NotNull
    private UUID domainId;
    @NotBlank
    private String templateKey;
    @NotBlank
    private String name;
    private String description;
    private String title;
}
