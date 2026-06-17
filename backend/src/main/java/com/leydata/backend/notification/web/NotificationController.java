package com.leydata.backend.notification.web;

import com.leydata.backend.notification.application.dto.NotificationResponse;
import com.leydata.backend.notification.application.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Notificaciones in-app del usuario autenticado")
public class NotificationController {

    private final NotificationService service;

    @GetMapping
    @Operation(summary = "Listar mis notificaciones (más recientes primero)")
    public List<NotificationResponse> getMyNotifications() {
        return service.getMyNotifications();
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Cantidad de notificaciones no leídas")
    public Map<String, Long> getUnreadCount() {
        return Map.of("count", service.getUnreadCount());
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Marcar una notificación como leída")
    public NotificationResponse markAsRead(@PathVariable UUID id) {
        return service.markAsRead(id);
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Marcar todas mis notificaciones como leídas")
    public void markAllAsRead() {
        service.markAllAsRead();
    }
}
