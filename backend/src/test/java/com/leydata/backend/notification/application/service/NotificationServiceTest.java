package com.leydata.backend.notification.application.service;

import com.leydata.backend.entity.Notification;
import com.leydata.backend.notification.application.dto.NotificationResponse;
import com.leydata.backend.notification.domain.enums.NotificationType;
import com.leydata.backend.notification.infrastructure.persistence.NotificationRepository;
import com.leydata.backend.shared.SecurityContextHelper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository repo;
    @Mock private SecurityContextHelper securityContextHelper;

    @InjectMocks
    private NotificationService service;

    private final String userId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        lenient().when(securityContextHelper.getKeycloakId()).thenReturn(userId);
        lenient().when(repo.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Notification notification(UUID id, String recipientId, boolean read) {
        return Notification.builder()
                .id(id)
                .recipientId(recipientId)
                .type(NotificationType.DOCUMENT_PUBLISHED)
                .title("Documento publicado")
                .message("El documento X fue publicado")
                .referenceId(UUID.randomUUID())
                .read(read)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ── create() ─────────────────────────────────────────────────────────────────

    @Test
    void create_creaNotificacionNoLeidaConLosDatosRecibidos() {
        UUID referenceId = UUID.randomUUID();

        service.create(userId, NotificationType.PURPOSE_APPROVED, "Finalidad aprobada", "Tu solicitud fue aprobada", referenceId);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repo).save(captor.capture());

        Notification saved = captor.getValue();
        assertThat(saved.getRecipientId()).isEqualTo(userId);
        assertThat(saved.getType()).isEqualTo(NotificationType.PURPOSE_APPROVED);
        assertThat(saved.getTitle()).isEqualTo("Finalidad aprobada");
        assertThat(saved.getMessage()).isEqualTo("Tu solicitud fue aprobada");
        assertThat(saved.getReferenceId()).isEqualTo(referenceId);
        assertThat(saved.getRead()).isFalse();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    // ── getMyNotifications() ─────────────────────────────────────────────────────

    @Test
    void getMyNotifications_devuelveNotificacionesDelUsuarioAutenticadoOrdenadas() {
        Notification n1 = notification(UUID.randomUUID(), userId, false);
        Notification n2 = notification(UUID.randomUUID(), userId, true);
        when(repo.findByRecipientIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(n1, n2));

        List<NotificationResponse> result = service.getMyNotifications();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(n1.getId());
        assertThat(result.get(1).id()).isEqualTo(n2.getId());
    }

    @Test
    void getMyNotifications_listaVacia_siNoHayNotificaciones() {
        when(repo.findByRecipientIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());

        List<NotificationResponse> result = service.getMyNotifications();

        assertThat(result).isEmpty();
    }

    // ── getUnreadCount() ─────────────────────────────────────────────────────────

    @Test
    void getUnreadCount_devuelveConteoDelUsuarioAutenticado() {
        when(repo.countByRecipientIdAndReadFalse(userId)).thenReturn(3L);

        long count = service.getUnreadCount();

        assertThat(count).isEqualTo(3L);
    }

    // ── markAsRead() ─────────────────────────────────────────────────────────────

    @Test
    void markAsRead_marcaComoLeidaYRetornaResponseActualizado() {
        UUID notificationId = UUID.randomUUID();
        Notification unread = notification(notificationId, userId, false);
        when(repo.findById(notificationId)).thenReturn(Optional.of(unread));

        NotificationResponse response = service.markAsRead(notificationId);

        assertThat(response.read()).isTrue();
        verify(repo).save(unread);
    }

    @Test
    void markAsRead_lanzaExcepcion_siLaNotificacionNoExiste() {
        UUID notificationId = UUID.randomUUID();
        when(repo.findById(notificationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAsRead(notificationId))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void markAsRead_lanzaExcepcion_siLaNotificacionNoPerteneceAlUsuarioAutenticado() {
        UUID notificationId = UUID.randomUUID();
        Notification ajena = notification(notificationId, "otro-usuario-keycloak-id", false);
        when(repo.findById(notificationId)).thenReturn(Optional.of(ajena));

        assertThatThrownBy(() -> service.markAsRead(notificationId))
                .isInstanceOf(IllegalArgumentException.class);

        verify(repo, never()).save(any(Notification.class));
    }

    // ── markAllAsRead() ──────────────────────────────────────────────────────────

    @Test
    void markAllAsRead_delegaAlRepositorioConElUsuarioAutenticado() {
        service.markAllAsRead();

        verify(repo, times(1)).markAllAsRead(userId);
    }
}
