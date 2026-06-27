package com.leydata.backend.purposedatacategory.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "Política de retención para una combinación finalidad-categoría")
public class DataRetentionPolicyRequest {

    @NotNull
    @Min(1)
    @Schema(description = "Período de retención (número entero positivo)",
            example = "5")
    private Integer retentionPeriod;

    @NotBlank
    @Pattern(regexp = "DAYS|MONTHS|YEARS",
             message = "retentionUnit debe ser DAYS, MONTHS o YEARS")
    @Schema(description = "Unidad del período de retención",
            example = "YEARS",
            allowableValues = {"DAYS", "MONTHS", "YEARS"})
    private String retentionUnit;

    @Schema(description = "Justificación legal del período (ej: artículo de ley que lo exige)",
            example = "Art. 17 Ley 21.719 — datos de salud ocupacional",
            nullable = true)
    private String legalJustification;

    @NotNull
    @Schema(description = "true = anonimizar los datos al cumplirse el período en lugar de eliminarlos",
            example = "true")
    private Boolean anonymizeAfter;
}
