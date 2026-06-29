package com.leydata.backend.audit.application.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class AuditContext {

    private final String tableName;
    private final UUID recordId;
    private final String action;
    private final Object oldData;
    private final Object newData;

    // keycloak_id (sub del JWT) del operador — estable aunque cambie su email
    private final String actorId;

    private final String actorRole;
}
