package com.leydata.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserByAdminRequest {

    private String password;
    private String name;
    private List<String> roleCodes; // Lista de códigos de roles
    private List<UUID> domainIds; // Lista de IDs de dominios
    private Boolean active;
}