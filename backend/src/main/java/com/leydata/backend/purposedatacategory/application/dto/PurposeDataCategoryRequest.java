package com.leydata.backend.purposedatacategory.application.dto;

import com.leydata.backend.purposedatacategory.domain.enums.DataUseType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Set;
import java.util.UUID;

@Data
public class PurposeDataCategoryRequest {

    @NotNull
    private UUID dataCategoryId;

    // true = este tipo de dato es obligatorio para la finalidad (no se puede omitir)
    @NotNull
    private Boolean required;

    // Al menos un uso declarado es obligatorio (Ley 21.719 exige declarar cómo se usa el dato)
    @NotEmpty(message = "Debe declarar al menos un uso de la categoría de datos")
    private Set<DataUseType> dataUses;

    // La política de retención se define en el momento del vínculo — no puede quedar sin definir
    @NotNull
    @Valid
    private DataRetentionPolicyRequest retention;
}
