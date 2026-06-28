package com.leydata.backend.user.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private String keycloakId;
    private String email;
    private String name;
    private Boolean active;
    private Boolean blocked;
    private List<String> roles;
    private List<String> domains;
}
