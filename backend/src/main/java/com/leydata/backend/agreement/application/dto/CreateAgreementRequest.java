package com.leydata.backend.agreement.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class CreateAgreementRequest {

    // UUID interno del titular (uso directo por la app web)
    private UUID dataSubjectId;

    // Identificador opaco del titular (uso B2B vía Orquestador, ej: "RUT:12345678-9")
    // Si dataSubjectId es null, se hace findOrCreate por este campo
    private String subjectIdentifier;

    @NotNull(message = "El templateId es obligatorio")
    private UUID templateId;

    @NotNull(message = "El documentId es obligatorio")
    private UUID documentId;

    @NotEmpty(message = "Debe incluir al menos una decisión de purpose")
    @Valid
    private List<PurposeDecisionRequest> purposes;

    @Valid
    private AgreementMetadataRequest metadata;
}
