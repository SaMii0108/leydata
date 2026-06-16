package com.leydata.backend.LegalBasis;

import com.leydata.backend.entity.LegalBasisCatalog;
import java.util.UUID;

public record LegalBasisResponseDto(
    UUID id,
    String code,
    String name,
    String description,
    Boolean consentRequired
) {
    public static LegalBasisResponseDto from(LegalBasisCatalog entity) {
        return new LegalBasisResponseDto(
            entity.getId(),
            entity.getCode(),
            entity.getName(),
            entity.getDescription(),
            entity.getConsentRequired()
        );
    }
}
