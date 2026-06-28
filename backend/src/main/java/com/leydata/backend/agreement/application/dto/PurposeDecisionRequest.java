package com.leydata.backend.agreement.application.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class PurposeDecisionRequest {

    @NotNull(message = "El purposeId es obligatorio")
    private UUID purposeId;

    @NotNull(message = "El campo 'accepted' es obligatorio")
    private Boolean accepted;
}
