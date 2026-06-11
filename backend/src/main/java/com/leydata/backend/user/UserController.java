package com.leydata.backend.user;

import com.leydata.backend.entity.Users;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

//Controlador de gestión de usuarios.
//La autenticación (login, logout, contraseñas) es responsabilidad de Keycloak.
//Este controlador gestiona el ciclo de vida del usuario dentro de la aplicación:
//creación, edición, bloqueo, desactivación y reactivación.
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    //POST /api/users — crea el usuario en Keycloak Y en la BD local en una sola operación.
    //El admin solo necesita llamar este endpoint; no hay que hacer nada manual en Keycloak.
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody CreateUserRequest request) {
        try {
            Users created = userService.createUser(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "status", "success",
                    "message", "Usuario creado correctamente en el sistema y en autenticación.",
                    "userId", created.getId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Error interno al crear usuario"));
        }
    }

    //GET /api/users — listar todos los usuarios con sus roles y dominios
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<?> getAllUsers() {
        List<UserSummaryDto> users = userService.getAllUsers();
        return ResponseEntity.ok(Map.of("status", "success", "users", users));
    }

    //GET /api/users/{userId} — obtener detalle de un usuario por ID
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{userId}")
    public ResponseEntity<?> getUserById(@PathVariable UUID userId) {
        try {
            UserSummaryDto user = userService.getUserById(userId);
            return ResponseEntity.ok(Map.of("status", "success", "user", user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    //PUT /api/users/{userId} — editar nombre, roles y dominios de un usuario
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{userId}")
    public ResponseEntity<?> updateUserByAdmin(@PathVariable UUID userId,
            @RequestBody UpdateUserByAdminRequest request) {
        try {
            UserSummaryDto updated = userService.updateUserByAdmin(userId, request);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Usuario actualizado correctamente",
                    "user", updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    //POST /api/users/{userId}/block — bloqueo permanente e irreversible desde la API
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{userId}/block")
    public ResponseEntity<?> blockUser(@PathVariable UUID userId) {
        try {
            UserSummaryDto blocked = userService.blockUser(userId);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Usuario bloqueado permanentemente",
                    "user", blocked));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    //POST /api/users/{userId}/deactivate — suspensión temporal (reversible)
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{userId}/deactivate")
    public ResponseEntity<?> deactivateUser(@PathVariable UUID userId) {
        try {
            UserSummaryDto deactivated = userService.deactivateUser(userId);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Usuario desactivado correctamente",
                    "user", deactivated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    //POST /api/users/{userId}/reactivate — volver a activar un usuario desactivado
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{userId}/reactivate")
    public ResponseEntity<?> reactivateUser(@PathVariable UUID userId) {
        try {
            UserSummaryDto reactivated = userService.reactivateUser(userId);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Usuario reactivado correctamente",
                    "user", reactivated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }
}
