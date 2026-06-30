package com.leydata.backend.purposerequest.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Decisión del DPO sobre una solicitud de finalidad")
public class ReviewRequestDto {

    @Schema(description = "Decisión del DPO",
            example = "APPROVED",
            allowableValues = {"APPROVED", "REJECTED"})
    private String status;

    @Schema(description = "Notas de revisión. Obligatorio cuando status=REJECTED (Ley 21.719 exige justificar el rechazo).",
            example = "Finalidad válida bajo Art. 12 Ley 21.719 — consentimiento explícito del titular")
    private String reviewNotes;
}
