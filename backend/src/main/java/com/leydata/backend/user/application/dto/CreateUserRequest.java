package com.leydata.backend.user.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

// La contraseña se envía solo para Keycloak — nunca se almacena en nuestra BD.
// keycloakId es interno: lo asigna el backend con el ID que retorna Keycloak al crear el usuario.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequest {
    private String password;
    private String email;
    private String name;
    private String roleCode;
    private List<UUID> domainIds;
    private String keycloakId;
}
