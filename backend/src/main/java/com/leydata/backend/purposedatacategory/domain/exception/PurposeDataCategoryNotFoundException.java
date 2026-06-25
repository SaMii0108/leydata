package com.leydata.backend.purposedatacategory.domain.exception;

import java.util.UUID;

public class PurposeDataCategoryNotFoundException extends RuntimeException {
    public PurposeDataCategoryNotFoundException(UUID id) {
        super("Vínculo categoría-finalidad no encontrado: " + id);
    }
}
