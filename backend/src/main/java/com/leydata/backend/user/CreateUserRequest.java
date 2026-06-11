package com.leydata.backend.user;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

//Request para crear un usuario desde el backoffice de Ley Data.
//El backend crea el usuario en Keycloak Y en la BD local en una sola operación.
//La contraseña se envía solo para Keycloak — nunca se almacena en nuestra BD.
//
//keycloakId es interno: no debe enviarse desde el frontend. El backend lo obtiene
//automáticamente de la respuesta de Keycloak al crear el usuario.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequest {
    //Contraseña inicial del usuario. Solo va a Keycloak, nunca se persiste en nuestra BD.
    private String password;
    private String email;
    private String name;
    private String roleCode;
    //Solo para usuarios con rol JEFE_DOMINIO
    private List<UUID> domainIds;
    //Uso interno: el backend lo llena con el ID que retorna Keycloak. No enviar desde el frontend.
    private String keycloakId;
}
