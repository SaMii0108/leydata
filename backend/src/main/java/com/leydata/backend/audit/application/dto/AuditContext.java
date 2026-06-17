package com.leydata.backend.audit.application.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

// Objeto inmutable que encapsula todos los datos de un evento a auditar.
// Se construye con el patrón Builder para que cada campo sea explícito en el llamador.
@Getter
@Builder
public class AuditContext {

    // Nombre de la tabla afectada: "users", "domains", "purpose_requests"
    private final String tableName;

    // UUID del registro afectado (el usuario creado, el dominio desactivado, etc.)
    private final UUID recordId;

    // Acción ejecutada. Valores posibles:
    //   Usuarios:   CREAR_USUARIO, EDITAR_USUARIO, DESACTIVAR_USUARIO, REACTIVAR_USUARIO, BLOQUEAR_USUARIO
    //   Dominios:   CREAR_DOMINIO, DESACTIVAR_DOMINIO, REACTIVAR_DOMINIO
    //   Propósitos: SOLICITAR_PROPOSITO, APROBAR_SOLICITUD, RECHAZAR_SOLICITUD
    private final String action;

    // Estado anterior del registro (null para creaciones). Se serializa a JSON antes de guardarse.
    private final Object oldData;

    // Estado posterior del registro. Se serializa a JSON antes de guardarse.
    private final Object newData;

    // UUID del operador que ejecutó la acción (ID local en nuestra BD)
    private final UUID actorId;

    // Rol del operador al momento de ejecutar la acción: "ADMIN", "DPO", "JEFE_DOMINIO"
    private final String actorRole;
}
