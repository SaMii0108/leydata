package com.leydata.backend.purposes.application.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class UpdatePurposeRequest {
    // code y domainId son inmutables — no se exponen aquí
    private String name;
    private String description;
    private String shortDescription;
    private Boolean required;
    private Boolean revocable;
    private Integer presentationOrder;
    private UUID legalBasisId;
    private String consentStatement;
}
