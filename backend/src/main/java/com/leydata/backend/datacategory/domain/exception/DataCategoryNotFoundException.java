package com.leydata.backend.datacategory.domain.exception;

import java.util.UUID;

public class DataCategoryNotFoundException extends RuntimeException {
    public DataCategoryNotFoundException(UUID id) {
        super("Categoría de datos no encontrada: " + id);
    }
    public DataCategoryNotFoundException(String code) {
        super("Categoría de datos no encontrada: " + code);
    }
}
