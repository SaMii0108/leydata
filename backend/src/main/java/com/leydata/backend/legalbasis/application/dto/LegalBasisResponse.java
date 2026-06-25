package com.leydata.backend.legalbasis.application.dto;

import com.leydata.backend.entity.LegalBasisCatalog;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class LegalBasisResponse {

    private UUID id;
    private String code;
    private String name;
    private String description;
    private Boolean consentRequired;
    private Boolean isActive;

    public static LegalBasisResponse from(LegalBasisCatalog e) {
        return LegalBasisResponse.builder()
                .id(e.getId())
                .code(e.getCode())
                .name(e.getName())
                .description(e.getDescription())
                .consentRequired(e.getConsentRequired())
                .isActive(e.getIsActive())
                .build();
    }
}
