package com.leydata.backend.template.application.dto;

import lombok.Data;

@Data
public class UpdateTemplatePurposeRequest {
    private Integer orderPosition;
    private Boolean isVisible;
}
