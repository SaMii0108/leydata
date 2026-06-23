package com.leydata.backend.notification.application.service;

import com.leydata.backend.entity.Notification;
import com.leydata.backend.notification.application.dto.NotificationResponse;
import com.leydata.backend.notification.domain.enums.NotificationType;
import com.leydata.backend.notification.infrastructure.persistence.NotificationRepository;
import com.leydata.backend.shared.SecurityContextHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repo;
    private final SecurityContextHelper securityContextHelper;

    public void create(UUID recipientId, NotificationType type, String title, String message, UUID referenceId) {
        Notification n = Notification.builder()
                .recipientId(recipientId)
                .type(type)
                .title(title)
                .message(message)
                .referenceId(referenceId)
                .read(false)
                .createdAt(LocalDateTime.now())
                .build();
        repo.save(n);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getMyNotifications() {
        UUID userId = securityContextHelper.getAuthenticatedUser().getId();
        return repo.findByRecipientIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount() {
        UUID userId = securityContextHelper.getAuthenticatedUser().getId();
        return repo.countByRecipientIdAndReadFalse(userId);
    }

    @Transactional
    public NotificationResponse markAsRead(UUID notificationId) {
        UUID userId = securityContextHelper.getAuthenticatedUser().getId();
        Notification n = repo.findById(notificationId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Notificación no encontrada: " + notificationId));
        if (!n.getRecipientId().equals(userId)) {
            throw new IllegalArgumentException("No puedes marcar notificaciones de otro usuario");
        }
        n.setRead(true);
        return NotificationResponse.from(repo.save(n));
    }

    @Transactional
    public void markAllAsRead() {
        UUID userId = securityContextHelper.getAuthenticatedUser().getId();
        repo.markAllAsRead(userId);
    }
}
