package com.leydata.backend.purposes.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CreatePurposeRequest {

    @NotBlank(message = "El código es obligatorio")
    private String code;

    @NotBlank(message = "El nombre es obligatorio")
    private String name;

    @NotBlank(message = "La descripción es obligatoria")
    private String description;

    private String shortDescription;

    @NotNull(message = "El campo 'required' es obligatorio")
    private Boolean required;

    @NotNull(message = "El campo 'revocable' es obligatorio")
    private Boolean revocable;

    private Integer presentationOrder;

    @NotNull(message = "La base de licitud es obligatoria")
    private UUID legalBasisId;

    @NotNull(message = "El dominio es obligatorio")
    private UUID domainId;

    private UUID purposeRequestId;

    // Texto exacto que el titular lee y acepta al otorgar consentimiento
    private String consentStatement;
}
