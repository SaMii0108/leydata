package com.leydata.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Une la notificación con la acción exacta
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private ConsentTransactionLog transaction;

    // Indica qué se envió
    @Column(name = "notification_type", nullable = false)
    private String notificationType;

    // Indica el estado de entrega
    @Column(name = "delivery_status", nullable = false)
    private String deliveryStatus;

    @Column(name = "server_response", columnDefinition = "TEXT")
    private String serverResponse; // ej: "250 OK - Message accepted"

    // La fecha y hora exacta del intento de envío
    @Column(name = "attempt_date", nullable = false)
    private LocalDateTime attemptDate;
}