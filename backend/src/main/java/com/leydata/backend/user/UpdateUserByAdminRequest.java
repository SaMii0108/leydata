package com.leydata.backend.user;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

//Request para que el ADMIN edite datos de un usuario.
//La contraseña es gestionada por Keycloak; este endpoint solo modifica
//datos de la aplicación: nombre, roles y dominios asignados.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserByAdminRequest {
    private String name;
    private List<String> roleCodes;
    private List<UUID> domainIds;
}
