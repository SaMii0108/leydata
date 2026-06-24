package com.leydata.backend.privacydoc.application.dto;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data
@Builder
public class VerifyResponse {
    private UUID documentId;
    private Integer version;
    private boolean hashMatch;
    private String storedHash;
    private String computedHash;
    private String message;
}
