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

//KeycloakAdminService — gestiona la comunicación con la API de administración de Keycloak.
//Permite crear, eliminar y configurar usuarios directamente desde el backend,
//usando un service account (leydata-backend) con permisos de manage-users.
//
//Flujo de autenticación: client_credentials grant con el secret del cliente leydata-backend.
//El token se cachea y se renueva automáticamente antes de que expire.
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

    //Token cacheado para no pedir uno nuevo en cada operación
    private String cachedToken;
    private long tokenExpiresAt = 0;

    public KeycloakAdminService(ObjectMapper objectMapper) {
        this.restClient = RestClient.create();
        this.objectMapper = objectMapper;
    }

    //Obtiene un token de admin usando client_credentials.
    //Lo cachea hasta 30 segundos antes de que expire para evitar requests fallidos.
    private String getAdminToken() {
        if (cachedToken != null && System.currentTimeMillis() < tokenExpiresAt) {
            return cachedToken;
        }

        String body = "grant_type=client_credentials"
                + "&client_id=" + clientId
                + "&client_secret=" + clientSecret;

        String response = restClient.post()
                .uri(serverUrl + "/realms/" + realm + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(String.class);

        Map<?, ?> tokenResponse = objectMapper.readValue(response, Map.class);
        cachedToken = (String) tokenResponse.get("access_token");
        int expiresIn = (Integer) tokenResponse.get("expires_in");

        //Guardar cuándo expira con 30s de margen
        tokenExpiresAt = System.currentTimeMillis() + ((long)(expiresIn - 30) * 1000);

        return cachedToken;
    }

    //Crea un usuario en Keycloak, le asigna contraseña y realm role.
    //Retorna el keycloak_id (UUID) asignado por Keycloak al nuevo usuario.
    //
    //Si algo falla, lanza RuntimeException para que UserService haga el rollback.
    public String createUser(String email, String name, String roleCode, String password) {
        String token = getAdminToken();

        //Paso 1: crear el usuario en Keycloak
        //Dividir el nombre en firstName/lastName porque Keycloak los requiere no-vacíos (User Profile)
        //Si el nombre tiene una sola palabra, se usa tanto en firstName como lastName
        String[] nameParts = name.trim().split("\\s+", 2);
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

        //Keycloak retorna la URL del nuevo usuario en el header Location
        //Ejemplo: http://localhost:8180/admin/realms/leydata/users/uuid-aqui
        String location = createResponse.getHeaders().getFirst("Location");
        if (location == null || location.isBlank()) {
            throw new RuntimeException("Keycloak no retornó el ID del usuario creado (Location header ausente)");
        }
        String keycloakId = location.substring(location.lastIndexOf("/") + 1);

        //Paso 2: asignar contraseña inicial (no temporal — el usuario puede entrar directo)
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

        //Paso 3: obtener el realm role por nombre y asignarlo al usuario
        String roleJson = restClient.get()
                .uri(serverUrl + "/admin/realms/" + realm + "/roles/" + roleCode)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(String.class);

        Map<?, ?> role = objectMapper.readValue(roleJson, Map.class);

        restClient.post()
                .uri(serverUrl + "/admin/realms/" + realm + "/users/" + keycloakId + "/role-mappings/realm")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(role))
                .retrieve()
                .toBodilessEntity();

        return keycloakId;
    }

    //Elimina un usuario de Keycloak por su keycloak_id.
    //Se usa como compensación si la creación en BD local falla después de haber creado en Keycloak.
    //Los errores se suprimen intencionalmente: si esto falla, se loguea pero no se relanza.
    public void deleteUser(String keycloakId) {
        try {
            String token = getAdminToken();
            restClient.delete()
                    .uri(serverUrl + "/admin/realms/" + realm + "/users/" + keycloakId)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            //Log del error pero no se relanza: esto es una compensación, no la operación principal
            System.err.println("[KeycloakAdminService] Fallo al eliminar usuario " + keycloakId
                    + " de Keycloak durante compensación: " + e.getMessage());
        }
    }
}
