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

    @NotNull(message = "El dataSubjectId es obligatorio")
    private UUID dataSubjectId;

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
