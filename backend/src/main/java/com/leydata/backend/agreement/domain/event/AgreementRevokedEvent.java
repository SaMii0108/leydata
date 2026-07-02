package com.leydata.backend.agreement.domain.event;

import java.util.List;
import java.util.UUID;

public record AgreementRevokedEvent(
        String subjectIdentifier,   // ID opaco del titular (DataSubjects.identifier)
        List<UUID> purposeIds       // UUIDs de los purposes revocados
) {}
