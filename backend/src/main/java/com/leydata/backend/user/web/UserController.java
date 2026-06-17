package com.leydata.backend.user.web;

import com.leydata.backend.user.application.dto.CreateUserRequest;
import com.leydata.backend.user.application.dto.UpdateUserByAdminRequest;
import com.leydata.backend.user.application.dto.UserResponse;
import com.leydata.backend.user.application.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// Gestión del ciclo de vida del usuario dentro de la aplicación: creación, edición,
// bloqueo, desactivación y reactivación. La autenticación es responsabilidad de Keycloak.
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // POST /api/users — crea el usuario en Keycloak Y en la BD local en una sola operación.
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createUser(@RequestBody CreateUserRequest request) {
        UserResponse created = userService.createUser(request);
        return Map.of(
                "status", "success",
                "message", "Usuario creado correctamente en el sistema y en autenticación.",
                "userId", created.getId());
    }

    // GET /api/users — listar todos los usuarios con sus roles y dominios
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public Map<String, Object> getAllUsers() {
        List<UserResponse> users = userService.getAllUsers();
        return Map.of("status", "success", "users", users);
    }

    // GET /api/users/{userId} — obtener detalle de un usuario por ID
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{userId}")
    public Map<String, Object> getUserById(@PathVariable UUID userId) {
        UserResponse user = userService.getUserById(userId);
        return Map.of("status", "success", "user", user);
    }

    // PUT /api/users/{userId} — editar nombre, roles y dominios de un usuario
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{userId}")
    public Map<String, Object> updateUserByAdmin(@PathVariable UUID userId,
            @RequestBody UpdateUserByAdminRequest request) {
        UserResponse updated = userService.updateUserByAdmin(userId, request);
        return Map.of(
                "status", "success",
                "message", "Usuario actualizado correctamente",
                "user", updated);
    }

    // POST /api/users/{userId}/block — bloqueo permanente e irreversible desde la API
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{userId}/block")
    public Map<String, Object> blockUser(@PathVariable UUID userId) {
        UserResponse blocked = userService.blockUser(userId);
        return Map.of(
                "status", "success",
                "message", "Usuario bloqueado permanentemente",
                "user", blocked);
    }

    // POST /api/users/{userId}/deactivate — suspensión temporal (reversible)
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{userId}/deactivate")
    public Map<String, Object> deactivateUser(@PathVariable UUID userId) {
        UserResponse deactivated = userService.deactivateUser(userId);
        return Map.of(
                "status", "success",
                "message", "Usuario desactivado correctamente",
                "user", deactivated);
    }

    // POST /api/users/{userId}/reactivate — volver a activar un usuario desactivado
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{userId}/reactivate")
    public Map<String, Object> reactivateUser(@PathVariable UUID userId) {
        UserResponse reactivated = userService.reactivateUser(userId);
        return Map.of(
                "status", "success",
                "message", "Usuario reactivado correctamente",
                "user", reactivated);
    }
}
