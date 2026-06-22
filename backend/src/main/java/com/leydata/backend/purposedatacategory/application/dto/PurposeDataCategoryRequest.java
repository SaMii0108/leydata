package com.leydata.backend.purposedatacategory.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class PurposeDataCategoryRequest {

    @NotNull
    private UUID dataCategoryId;

    // true = este tipo de dato es obligatorio para la finalidad (no se puede omitir)
    @NotNull
    private Boolean required;

    // La política de retención se define en el momento del vínculo — no puede quedar sin definir
    @NotNull
    @Valid
    private DataRetentionPolicyRequest retention;
}
