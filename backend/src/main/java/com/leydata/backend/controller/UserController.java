package com.leydata.backend.controller;

import com.leydata.backend.dto.CreateUserRequest;
import com.leydata.backend.dto.UpdateAdminProfileRequest;
import com.leydata.backend.dto.UpdateUserProfileRequest;
import com.leydata.backend.dto.UpdateUserByAdminRequest;
import com.leydata.backend.dto.UserSummaryDto;
import com.leydata.backend.entity.Users;
import com.leydata.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // POST /api/users crear usuario (solo ADMIN)
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody CreateUserRequest request) {
        try {
            Users created = userService.createUser(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "status", "success",
                    "message", "Usuario creado correctamente",
                    "userId", created.getId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/users listar todos (solo ADMIN)
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<?> getAllUsers() {
        try {
            List<UserSummaryDto> users = userService.getAllUsers();
            return ResponseEntity.ok(Map.of("status", "success", "users", users));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/users/{userId} obtener por ID (solo ADMIN)
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

    // PUT /api/users/profile actualizar propio perfil (cualquier rol autenticado)
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/profile")
    public ResponseEntity<?> updateUserProfile(Authentication auth,
            @RequestBody UpdateUserProfileRequest request) {
        try {
            userService.updateUserProfile(auth.getName(), request);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Perfil actualizado correctamente"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // PUT /api/users/profile/admin actualizar perfil admin (solo ADMIN)
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/profile/admin")
    public ResponseEntity<?> updateAdminProfile(@RequestBody UpdateAdminProfileRequest request) {
        try {
            userService.updateAdminProfile(request);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Perfil actualizado correctamente"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // PUT /api/users/{userId} editar usuario (solo ADMIN)
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

    // POST /api/users/{userId}/block bloqueo permanente (solo ADMIN)
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
}