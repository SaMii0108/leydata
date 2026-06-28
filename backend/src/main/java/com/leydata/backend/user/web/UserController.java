package com.leydata.backend.user.web;

import com.leydata.backend.user.application.dto.CreateUserRequest;
import com.leydata.backend.user.application.dto.UpdateUserByAdminRequest;
import com.leydata.backend.user.application.dto.UserResponse;
import com.leydata.backend.user.application.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Usuarios", description = """
        Ciclo de vida de operadores del sistema. Requiere rol ADMIN.
        Modelo Keycloak-first: Keycloak es la fuente de verdad para identidad, contraseñas y roles.
        El identificador de usuario en todos los endpoints es el keycloak_id (claim "sub" del JWT).
        GET /api/users lista directamente desde Keycloak, sin tabla local.
        """)
public class UserController {

    private final UserService userService;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Crear usuario [ADMIN]",
            description = """
                    Crea el operador en Keycloak y registra sus dominios en la BD local.
                    Si la asignación de dominios falla, el usuario se elimina de Keycloak como compensación.

                    - `roleCode` válidos: `ADMIN`, `DPO`, `JEFE_DOMINIO`, `USER`, `TITULAR`
                    - `domainIds` solo aplica si `roleCode = JEFE_DOMINIO`
                    - La contraseña se envía a Keycloak y **nunca** se almacena localmente
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuario creado"),
            @ApiResponse(responseCode = "409", description = "Email ya registrado en Keycloak"),
            @ApiResponse(responseCode = "400", description = "Rol inválido o dominio inactivo"),
            @ApiResponse(responseCode = "403", description = "Sin rol ADMIN")
    })
    public Map<String, Object> createUser(@RequestBody CreateUserRequest request) {
        UserResponse created = userService.createUser(request);
        return Map.of(
                "status", "success",
                "message", "Usuario creado correctamente.",
                "keycloakId", created.getKeycloakId());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    @Operation(summary = "Listar usuarios [ADMIN]",
            description = """
                    Lista directamente desde Keycloak (fuente de verdad). No depende de tabla local.

                    Parámetros opcionales (usar uno a la vez):
                    - `search` — busca por nombre, email o username en Keycloak
                    - `status` — filtra por estado: `active` | `inactive` | `blocked`
                    - `role`   — filtra por rol: `ADMIN` | `DPO` | `JEFE_DOMINIO` | `USER` | `TITULAR`
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de usuarios"),
            @ApiResponse(responseCode = "403", description = "Sin rol ADMIN")
    })
    public Map<String, Object> getAllUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role) {
        List<UserResponse> users = userService.getAllUsers(search, status, role);
        return Map.of("status", "success", "users", users);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{userId}")
    @Operation(summary = "Obtener usuario por keycloak_id [ADMIN]")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalle del usuario"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado en Keycloak"),
            @ApiResponse(responseCode = "403", description = "Sin rol ADMIN")
    })
    public Map<String, Object> getUserById(
            @Parameter(description = "keycloak_id del usuario (claim sub del JWT)") @PathVariable String userId) {
        UserResponse user = userService.getUserByKeycloakId(userId);
        return Map.of("status", "success", "user", user);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{userId}")
    @Operation(
            summary = "Editar usuario [ADMIN]",
            description = """
                    Actualiza nombre, email, contraseña, roles y dominios asignados en Keycloak.
                    Si se quita el rol `JEFE_DOMINIO`, los dominios del usuario se limpian automáticamente.
                    Los dominios solo se pueden asignar a usuarios con rol `JEFE_DOMINIO`.
                    Al cambiar la contraseña, Keycloak la marca como temporal (required action UPDATE_PASSWORD).
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario actualizado"),
            @ApiResponse(responseCode = "400", description = "Rol inválido o dominio inactivo"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado en Keycloak"),
            @ApiResponse(responseCode = "409", description = "Usuario bloqueado (no se puede modificar)")
    })
    public Map<String, Object> updateUserByAdmin(
            @PathVariable String userId,
            @RequestBody UpdateUserByAdminRequest request) {
        UserResponse updated = userService.updateUserByAdmin(userId, request);
        return Map.of(
                "status", "success",
                "message", "Usuario actualizado correctamente",
                "user", updated);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{userId}/block")
    @Operation(
            summary = "Bloquear usuario permanentemente [ADMIN]",
            description = """
                    Bloqueo permanente e irreversible desde la API.
                    1. `keycloak.disableUser()` — impide nuevos logins y renovación de tokens
                    2. Registra en `user_status` con blocked=true — el UserStatusFilter corta tokens existentes

                    Para desbloquear: habilitar en Keycloak Admin Console
                    + `DELETE FROM user_status WHERE keycloak_id = '<id>'` en PostgreSQL.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario bloqueado"),
            @ApiResponse(responseCode = "409", description = "Usuario ya estaba bloqueado"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    public Map<String, Object> blockUser(@PathVariable String userId) {
        UserResponse blocked = userService.blockUser(userId);
        return Map.of(
                "status", "success",
                "message", "Usuario bloqueado permanentemente",
                "user", blocked);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{userId}/deactivate")
    @Operation(
            summary = "Desactivar usuario [ADMIN]",
            description = """
                    Suspensión temporal reversible.
                    1. `keycloak.disableUser()` — impide nuevos logins y renovación de tokens
                    Para revertir: usar `/reactivate`.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario desactivado"),
            @ApiResponse(responseCode = "409", description = "Usuario ya desactivado o bloqueado"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    public Map<String, Object> deactivateUser(@PathVariable String userId) {
        UserResponse deactivated = userService.deactivateUser(userId);
        return Map.of(
                "status", "success",
                "message", "Usuario desactivado correctamente",
                "user", deactivated);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{userId}/reactivate")
    @Operation(summary = "Reactivar usuario desactivado [ADMIN]",
            description = """
                    Revierte una desactivación temporal.
                    1. `keycloak.enableUser()` — restaura la capacidad de login en Keycloak
                    No aplica a usuarios bloqueados permanentemente.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario reactivado"),
            @ApiResponse(responseCode = "409", description = "Usuario ya activo o está bloqueado"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    public Map<String, Object> reactivateUser(@PathVariable String userId) {
        UserResponse reactivated = userService.reactivateUser(userId);
        return Map.of(
                "status", "success",
                "message", "Usuario reactivado correctamente",
                "user", reactivated);
    }
}
