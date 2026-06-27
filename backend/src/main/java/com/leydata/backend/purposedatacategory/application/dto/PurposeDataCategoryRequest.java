package com.leydata.backend.purposedatacategory.application.dto;

import com.leydata.backend.purposedatacategory.domain.enums.DataUseType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Set;
import java.util.UUID;

@Data
@Schema(description = "Vínculo entre una finalidad y una categoría de datos, con política de retención")
public class PurposeDataCategoryRequest {

    @NotNull
    @Schema(description = "UUID de la categoría de datos del catálogo (/api/data-categories)",
            example = "550e8400-e29b-41d4-a716-446655440001")
    private UUID dataCategoryId;

    @NotNull
    @Schema(description = "true = este tipo de dato es obligatorio para la finalidad (no se puede omitir al obtener consentimiento)",
            example = "true")
    private Boolean required;

    @NotEmpty(message = "Debe declarar al menos un uso de la categoría de datos")
    @Schema(description = "Usos declarados del dato. Mínimo uno requerido (Ley 21.719). " +
            "Valores: STORAGE, PROCESSING, TRANSFER_TO_THIRD_PARTIES, PROFILING, ANALYSIS",
            example = "[\"STORAGE\", \"PROCESSING\"]")
    private Set<DataUseType> dataUses;

    @NotNull
    @Valid
    @Schema(description = "Política de retención obligatoria — cuánto tiempo y bajo qué criterio se retiene este dato")
    private DataRetentionPolicyRequest retention;
}
