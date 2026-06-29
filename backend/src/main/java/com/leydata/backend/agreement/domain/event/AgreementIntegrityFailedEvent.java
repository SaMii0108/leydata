package com.leydata.backend.agreement.domain.event;

import lombok.Getter;

import java.util.UUID;

@Getter
public class AgreementIntegrityFailedEvent {
    private final UUID agreementId;
    private final String storedHash;
    private final String recalculatedHash;

    public AgreementIntegrityFailedEvent(UUID agreementId, String storedHash, String recalculatedHash) {
        this.agreementId = agreementId;
        this.storedHash = storedHash;
        this.recalculatedHash = recalculatedHash;
    }
}
