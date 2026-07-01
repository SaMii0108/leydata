package com.leydata.backend.audit.application.dto;

import lombok.Value;

import java.util.UUID;

@Value
public class VerifyIntegrityRequest {
    String entityType;
    UUID entityId;
    String checkType;
}
