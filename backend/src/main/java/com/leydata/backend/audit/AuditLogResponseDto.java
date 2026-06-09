package com.leydata.backend.audit;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

//DTO de respuesta para los endpoints de consulta de auditoría.
//Expone todos los campos del log excepto previousLogHash (detalle interno de la cadena).
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogResponseDto {
    private UUID id;
    private String tableName;
    private UUID recordId;
    private String action;
    private String oldData;
    private String newData;
    private UUID actorId;
    private String actorRole;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime createdAt;
    private String logHash;
}
