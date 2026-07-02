package com.leydata.backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class KeycloakAdminService {

    @Value("${keycloak.admin.server-url}")
    private String serverUrl;

    @Value("${keycloak.admin.realm}")
    private String realm;

    @Value("${keycloak.admin.client-id}")
    private String clientId;

    @Value("${keycloak.admin.client-secret}")
    private String clientSecret;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private String cachedToken;
    private long tokenExpiresAt = 0;

    public KeycloakAdminService(ObjectMapper objectMapper) {
        this.restClient = RestClient.create();
        this.objectMapper = objectMapper;
    }

    private String getAdminToken() {
        if (cachedToken != null && System.currentTimeMillis() < tokenExpiresAt) {
            return cachedToken;
        }

        String body = "grant_type=client_credentials"
                + "&client_id=" + clientId
                + "&client_secret=" + clientSecret;

        String response;
        try {
            response = restClient.post()
                    .uri(serverUrl + "/realms/" + realm + "/protocol/openid-connect/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 401) {
                throw new IllegalStateException(
                    "Las credenciales del cliente Keycloak son inválidas (KC_BACKEND_SECRET). " +
                    "Vuelve a ejecutar setup-keycloak.sh y actualiza .env con el nuevo valor.", e);
            }
            throw new RuntimeException("Error al autenticar con Keycloak: " + e.getMessage(), e);
        }

        Map<?, ?> tokenResponse = objectMapper.readValue(response, Map.class);
        cachedToken = (String) tokenResponse.get("access_token");
        int expiresIn = (Integer) tokenResponse.get("expires_in");
        tokenExpiresAt = System.currentTimeMillis() + ((long)(expiresIn - 30) * 1000);

        return cachedToken;
    }

    // Crea un usuario en Keycloak con password y realm role.
    // Retorna el keycloak_id asignado por Keycloak.
    public String createUser(String email, String name, String roleCode, String password) {
        String token = getAdminToken();

        String[] nameParts = name.trim().split("\s+", 2);
        String firstName = nameParts[0];
        String lastName = nameParts.length > 1 ? nameParts[1] : nameParts[0];

        Map<String, Object> userPayload = new HashMap<>();
        userPayload.put("username", email);
        userPayload.put("email", email);
        userPayload.put("firstName", firstName);
        userPayload.put("lastName", lastName);
        userPayload.put("enabled", true);
        userPayload.put("emailVerified", true);

        ResponseEntity<Void> createResponse;
        try {
            createResponse = restClient.post()
                    .uri(serverUrl + "/admin/realms/" + realm + "/users")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(userPayload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 409) {
                throw new IllegalArgumentException(
                        "El email " + email + " ya tiene una cuenta en el sistema de autenticación");
            }
            throw new RuntimeException("Error al crear usuario en Keycloak: " + e.getMessage(), e);
        }

        String location = createResponse.getHeaders().getFirst("Location");
        if (location == null || location.isBlank()) {
            throw new RuntimeException("Keycloak no retornó el ID del usuario creado (Location header ausente)");
        }
        String keycloakId = location.substring(location.lastIndexOf("/") + 1);

        Map<String, Object> passwordPayload = new HashMap<>();
        passwordPayload.put("type", "password");
        passwordPayload.put("value", password);
        passwordPayload.put("temporary", false);

        restClient.put()
                .uri(serverUrl + "/admin/realms/" + realm + "/users/" + keycloakId + "/reset-password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(passwordPayload)
                .retrieve()
                .toBodilessEntity();

        assignRealmRoles(token, keycloakId, List.of(roleCode));

        return keycloakId;
    }

    // Actualiza email y nombre de un usuario en Keycloak.
    public void updateUserProfile(String keycloakId, String email, String name) {
        String token = getAdminToken();
        Map<String, Object> payload = new HashMap<>();
        if (email != null) {
            payload.put("email", email);
            payload.put("username", email);
            payload.put("emailVerified", true);
        }
        if (name != null) {
            String[] parts = name.trim().split("\\s+", 2);
            payload.put("firstName", parts[0]);
            payload.put("lastName", parts.length > 1 ? parts[1] : parts[0]);
        }
        if (!payload.isEmpty()) {
            restClient.put()
                    .uri(serverUrl + "/admin/realms/" + realm + "/users/" + keycloakId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        }
    }

    // Resetea la contraseña de un usuario. temporary=true fuerza cambio en próximo login.
    public void resetPassword(String keycloakId, String newPassword, boolean temporary) {
        String token = getAdminToken();
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "password");
        payload.put("value", newPassword);
        payload.put("temporary", temporary);
        restClient.put()
                .uri(serverUrl + "/admin/realms/" + realm + "/users/" + keycloakId + "/reset-password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }

    // Reemplaza todos los realm roles de un usuario.
    // Quita los roles actuales (excepto los del sistema) y asigna los nuevos.
    public void updateUserRoles(String keycloakId, List<String> newRoleCodes) {
        String token = getAdminToken();

        // Obtener roles actuales del usuario
        String currentRolesJson = restClient.get()
                .uri(serverUrl + "/admin/realms/" + realm + "/users/" + keycloakId + "/role-mappings/realm")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(String.class);

        List<?> currentRoles = objectMapper.readValue(currentRolesJson, List.class);

        // Quitar todos los roles actuales
        if (!currentRoles.isEmpty()) {
            restClient.method(org.springframework.http.HttpMethod.DELETE)
                    .uri(serverUrl + "/admin/realms/" + realm + "/users/" + keycloakId + "/role-mappings/realm")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(currentRoles)
                    .retrieve()
                    .toBodilessEntity();
        }

        // Asignar los nuevos roles
        if (!newRoleCodes.isEmpty()) {
            assignRealmRoles(token, keycloakId, newRoleCodes);
        }
    }

    // Deshabilita un usuario en Keycloak: impide nuevos logins y refresh de tokens.
    public void disableUser(String keycloakId) {
        String token = getAdminToken();
        restClient.put()
                .uri(serverUrl + "/admin/realms/" + realm + "/users/" + keycloakId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("enabled", false))
                .retrieve()
                .toBodilessEntity();
    }

    // Habilita un usuario en Keycloak: restaura la capacidad de login.
    public void enableUser(String keycloakId) {
        String token = getAdminToken();
        restClient.put()
                .uri(serverUrl + "/admin/realms/" + realm + "/users/" + keycloakId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("enabled", true))
                .retrieve()
                .toBodilessEntity();
    }

    // Elimina un usuario de Keycloak. Se usa como compensación si falla la BD local.
    public void deleteUser(String keycloakId) {
        try {
            String token = getAdminToken();
            restClient.delete()
                    .uri(serverUrl + "/admin/realms/" + realm + "/users/" + keycloakId)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            System.err.println("[KeycloakAdminService] Fallo al eliminar usuario " + keycloakId
                    + " de Keycloak durante compensación: " + e.getMessage());
        }
    }

    // Retorna los datos de un usuario por su keycloak_id. Lanza UserNotFoundException si no existe.
    public Map<String, Object> getUser(String keycloakId) {
        String token = getAdminToken();
        String userJson;
        try {
            userJson = restClient.get()
                    .uri(serverUrl + "/admin/realms/" + realm + "/users/" + keycloakId)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(String.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 404) {
                throw new com.leydata.backend.user.domain.exception.UserNotFoundException(
                        "Usuario no encontrado en Keycloak: " + keycloakId);
            }
            throw new RuntimeException("Error al obtener usuario de Keycloak: " + e.getMessage(), e);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> u = objectMapper.readValue(userJson, Map.class);
        String firstName = (String) u.getOrDefault("firstName", "");
        String lastName  = (String) u.getOrDefault("lastName", "");
        String fullName  = (firstName + " " + lastName).trim();
        Map<String, Object> result = new HashMap<>();
        result.put("keycloakId", keycloakId);
        result.put("email",   u.get("email"));
        result.put("name",    fullName.isBlank() ? u.get("username") : fullName);
        result.put("enabled", u.getOrDefault("enabled", true));
        return result;
    }

    // Retorna todos los usuarios del realm con su nombre completo y roles asignados.
    // search: filtra por username/email/nombre en Keycloak (null = todos).
    public List<Map<String, Object>> listUsers(String search) {
        String token = getAdminToken();

        String uri = serverUrl + "/admin/realms/" + realm + "/users?max=200"
                + (search != null && !search.isBlank() ? "&search=" + search.trim() : "");

        String usersJson = restClient.get()
                .uri(uri)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(String.class);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawUsers = objectMapper.readValue(usersJson, List.class);

        return rawUsers.stream().map(u -> {
            String kcId = (String) u.get("id");
            String firstName = (String) u.getOrDefault("firstName", "");
            String lastName = (String) u.getOrDefault("lastName", "");
            String fullName = (firstName + " " + lastName).trim();

            Map<String, Object> result = new HashMap<>();
            result.put("keycloakId", kcId);
            result.put("email", u.get("email"));
            result.put("name", fullName.isBlank() ? u.get("username") : fullName);
            result.put("enabled", u.getOrDefault("enabled", true));
            result.put("roleCodes", getUserRoles(kcId));
            return result;
        }).toList();
    }

    // Retorna los realm roles asignados a un usuario en Keycloak.
    public List<String> getUserRoles(String keycloakId) {
        String token = getAdminToken();

        String rolesJson = restClient.get()
                .uri(serverUrl + "/admin/realms/" + realm + "/users/" + keycloakId + "/role-mappings/realm")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(String.class);

        List<?> roles = objectMapper.readValue(rolesJson, List.class);
        return roles.stream()
                .map(r -> (String) ((Map<?, ?>) r).get("name"))
                .filter(name -> !name.startsWith("default-roles") && !name.equals("offline_access") && !name.equals("uma_authorization"))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private void assignRealmRoles(String token, String keycloakId, List<String> roleCodes) {
        List<Map<String, Object>> roleRepresentations = roleCodes.stream().map(code -> {
            String roleJson = restClient.get()
                    .uri(serverUrl + "/admin/realms/" + realm + "/roles/" + code)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(String.class);
            return (Map<String, Object>) objectMapper.readValue(roleJson, Map.class);
        }).toList();

        restClient.post()
                .uri(serverUrl + "/admin/realms/" + realm + "/users/" + keycloakId + "/role-mappings/realm")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(roleRepresentations)
                .retrieve()
                .toBodilessEntity();
    }
}
