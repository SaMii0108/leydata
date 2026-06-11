package com.leydata.backend.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String email;
    private List<String> roles;
    private boolean mustChangePassword; // nuevo campo para indicar si el usuario debe cambiar su contraseña
}