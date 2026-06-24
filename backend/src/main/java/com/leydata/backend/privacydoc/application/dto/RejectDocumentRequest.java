package com.leydata.backend.privacydoc.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RejectDocumentRequest {

    @NotBlank(message = "El motivo de rechazo es obligatorio")
    private String reason;
}
