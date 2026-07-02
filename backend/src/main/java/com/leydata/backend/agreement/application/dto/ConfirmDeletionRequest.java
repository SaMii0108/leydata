package com.leydata.backend.agreement.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class ConfirmDeletionRequest {
    @NotBlank
    private String subjectIdentifier;
    @NotNull
    private UUID purposeId;
    private LocalDateTime deletedAt;
}
