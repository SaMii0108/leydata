package com.leydata.backend.agreement.domain.exception;

import java.util.UUID;

public class AgreementNotFoundException extends RuntimeException {
    public AgreementNotFoundException(UUID id) {
        super("Agreement no encontrado: " + id);
    }
}
