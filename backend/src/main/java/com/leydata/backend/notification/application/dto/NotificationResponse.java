package com.leydata.backend.notification.application.dto;

import com.leydata.backend.entity.Notification;
import com.leydata.backend.notification.domain.enums.NotificationType;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        String title,
        String message,
        UUID referenceId,
        boolean read,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getType(),
                n.getTitle(),
                n.getMessage(),
                n.getReferenceId(),
                Boolean.TRUE.equals(n.getRead()),
                n.getCreatedAt()
        );
    }
}
