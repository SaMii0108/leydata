package com.leydata.backend.agreement.application.service;

import com.leydata.backend.agreement.application.dto.AgreementIntegrityLogResponse;
import com.leydata.backend.agreement.domain.event.AgreementIntegrityFailedEvent;
import com.leydata.backend.agreement.infrastructure.persistence.AgreementsRepository;
import com.leydata.backend.entity.Agreements;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgreementIntegritySchedulerTest {

    @Mock private AgreementsRepository agreementsRepo;
    @Mock private AgreementService agreementService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AgreementIntegrityScheduler scheduler;

    private Agreements agreementWithId(UUID id) {
        Agreements a = new Agreements();
        a.setId(id);
        return a;
    }

    private AgreementIntegrityLogResponse validResult(String storedHash) {
        return AgreementIntegrityLogResponse.builder()
                .isValid(true)
                .storedHash(storedHash)
                .recalculatedHash(storedHash)
                .checkType("SCHEDULED")
                .build();
    }

    private AgreementIntegrityLogResponse invalidResult(String storedHash, String recalculatedHash) {
        return AgreementIntegrityLogResponse.builder()
                .isValid(false)
                .storedHash(storedHash)
                .recalculatedHash(recalculatedHash)
                .checkType("SCHEDULED")
                .build();
    }

    @Test
    void verifyAllAgreements_recorreTodosYLlamaVerifyIntegrityParaCadaUno() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(agreementsRepo.findAll()).thenReturn(List.of(agreementWithId(id1), agreementWithId(id2)));
        when(agreementService.verifyIntegrity(id1, "SCHEDULED", null)).thenReturn(validResult("h1"));
        when(agreementService.verifyIntegrity(id2, "SCHEDULED", null)).thenReturn(validResult("h2"));

        scheduler.verifyAllAgreements();

        verify(agreementService, times(1)).verifyIntegrity(id1, "SCHEDULED", null);
        verify(agreementService, times(1)).verifyIntegrity(id2, "SCHEDULED", null);
    }

    @Test
    void verifyAllAgreements_siUnaVerificacionFalla_continuaConElResto() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(agreementsRepo.findAll()).thenReturn(List.of(agreementWithId(id1), agreementWithId(id2)));
        when(agreementService.verifyIntegrity(id1, "SCHEDULED", null))
                .thenThrow(new RuntimeException("error inesperado"));
        when(agreementService.verifyIntegrity(id2, "SCHEDULED", null)).thenReturn(validResult("h2"));

        scheduler.verifyAllAgreements();

        verify(agreementService, times(1)).verifyIntegrity(id1, "SCHEDULED", null);
        verify(agreementService, times(1)).verifyIntegrity(id2, "SCHEDULED", null);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void verifyAllAgreements_publicaEventoSoloCuandoEsInvalido() {
        UUID id = UUID.randomUUID();
        when(agreementsRepo.findAll()).thenReturn(List.of(agreementWithId(id)));
        when(agreementService.verifyIntegrity(id, "SCHEDULED", null))
                .thenReturn(invalidResult("stored", "recalculated"));

        scheduler.verifyAllAgreements();

        ArgumentCaptor<AgreementIntegrityFailedEvent> captor = ArgumentCaptor.forClass(AgreementIntegrityFailedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        assertThat(captor.getValue().getAgreementId()).isEqualTo(id);
        assertThat(captor.getValue().getStoredHash()).isEqualTo("stored");
        assertThat(captor.getValue().getRecalculatedHash()).isEqualTo("recalculated");
    }

    @Test
    void verifyAllAgreements_noPublicaNingunEvento_cuandoTodosSonValidos() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(agreementsRepo.findAll()).thenReturn(List.of(agreementWithId(id1), agreementWithId(id2)));
        when(agreementService.verifyIntegrity(id1, "SCHEDULED", null)).thenReturn(validResult("h1"));
        when(agreementService.verifyIntegrity(id2, "SCHEDULED", null)).thenReturn(validResult("h2"));

        scheduler.verifyAllAgreements();

        verify(eventPublisher, never()).publishEvent(any());
    }
}
