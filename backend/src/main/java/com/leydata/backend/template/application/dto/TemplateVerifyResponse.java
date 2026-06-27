package com.leydata.backend.template.application.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class TemplateVerifyResponse {
    private UUID templateId;
    private Integer version;
    private boolean hashMatch;
    private String storedHash;
    private String computedHash;
    private String message;
}
