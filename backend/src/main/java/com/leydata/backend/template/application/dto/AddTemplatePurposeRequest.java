package com.leydata.backend.template.application.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddTemplatePurposeRequest {

    @NotNull
    private UUID purposeId;
    @NotNull
    private Integer orderPosition;
    private Boolean isVisible = true;
}
