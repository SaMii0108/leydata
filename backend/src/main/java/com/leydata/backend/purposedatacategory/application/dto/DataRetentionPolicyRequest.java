package com.leydata.backend.purposedatacategory.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class DataRetentionPolicyRequest {

    @NotNull
    @Min(1)
    private Integer retentionPeriod;

    @NotBlank
    @Pattern(regexp = "DAYS|MONTHS|YEARS",
             message = "retentionUnit debe ser DAYS, MONTHS o YEARS")
    private String retentionUnit;

    // Justificación legal del período (ej: "Art. 17 Ley 21.719 — datos de salud")
    private String legalJustification;

    @NotNull
    private Boolean anonymizeAfter;
}
