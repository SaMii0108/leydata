package com.leydata.backend.audit.application.service;

import com.leydata.backend.audit.application.dto.AuditContext;
import com.leydata.backend.audit.infrastructure.persistence.SystemAuditLogRepository;
import com.leydata.backend.entity.SystemAuditLog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// No usa @Mock/@InjectMocks para AuditService porque el constructor solo cubre los
// campos final (auditLogRepository, objectMapper) — el campo `self` (auto-inyección
// @Lazy para que tryLog() pase por el proxy AOP) se setea manualmente por reflexión.
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock private SystemAuditLogRepository auditLogRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AuditService service;

    @BeforeEach
    void setUp() {
        service = new AuditService(auditLogRepository, objectMapper);
        ReflectionTestUtils.setField(service, "self", service);
    }

    private AuditContext context(Object oldData, Object newData) {
        return AuditContext.builder()
                .tableName("domains")
                .recordId(UUID.randomUUID())
                .action("CREAR_DOMINIO")
                .oldData(oldData)
                .newData(newData)
                .actorId("actor-keycloak-id")
                .actorRole("ADMIN")
                .build();
    }

    // ── log() ────────────────────────────────────────────────────────────────────

    @Test
    void log_primerRegistro_usaGenesisComoPreviousLogHash() {
        when(auditLogRepository.findTopByOrderByCreatedAtDesc()).thenReturn(Optional.empty());

        service.log(context(null, Map.of("active", true)));

        ArgumentCaptor<SystemAuditLog> captor = ArgumentCaptor.forClass(SystemAuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getPreviousLogHash()).isEqualTo("GENESIS");
        assertThat(captor.getValue().getLogHash()).isNotBlank();
    }

    @Test
    void log_conRegistroPrevio_encadenaConSuLogHash() {
        SystemAuditLog previous = new SystemAuditLog();
        previous.setLogHash("hash-del-log-anterior");
        when(auditLogRepository.findTopByOrderByCreatedAtDesc()).thenReturn(Optional.of(previous));

        service.log(context(null, Map.of("active", true)));

        ArgumentCaptor<SystemAuditLog> captor = ArgumentCaptor.forClass(SystemAuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getPreviousLogHash()).isEqualTo("hash-del-log-anterior");
    }

    @Test
    void log_serializaOldDataYNewDataAJson() {
        when(auditLogRepository.findTopByOrderByCreatedAtDesc()).thenReturn(Optional.empty());

        service.log(context(Map.of("active", true), Map.of("active", false)));

        ArgumentCaptor<SystemAuditLog> captor = ArgumentCaptor.forClass(SystemAuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getOldData()).contains("active");
        assertThat(captor.getValue().getNewData()).contains("active");
    }

    @Test
    void log_siFallaLaSerializacionJson_usaObjetoVacioSinPropagarLaExcepcion() {
        when(auditLogRepository.findTopByOrderByCreatedAtDesc()).thenReturn(Optional.empty());

        // Un Object plano sin propiedades no tiene serializador Jackson registrado —
        // dispara la rama catch de toJson(), que debe degradar a "{}" en vez de propagar.
        service.log(context(new Object(), null));

        ArgumentCaptor<SystemAuditLog> captor = ArgumentCaptor.forClass(SystemAuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getOldData()).isEqualTo("{}");
    }

    @Test
    void log_fueraDeContextoHttp_usaValoresPorDefecto() {
        when(auditLogRepository.findTopByOrderByCreatedAtDesc()).thenReturn(Optional.empty());

        service.log(context(null, null));

        ArgumentCaptor<SystemAuditLog> captor = ArgumentCaptor.forClass(SystemAuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getIpAddress()).isEqualTo("UNKNOWN");
        assertThat(captor.getValue().getUserAgent()).isEqualTo("UNKNOWN");
        assertThat(captor.getValue().getRequestId()).isNull();
    }

    @Test
    void log_adquiereElLockDeCadenaAntesDeLeerElUltimoHash() {
        when(auditLogRepository.findTopByOrderByCreatedAtDesc()).thenReturn(Optional.empty());

        service.log(context(null, null));

        verify(auditLogRepository).acquireAuditChainLock();
    }

    // ── tryLog() ─────────────────────────────────────────────────────────────────

    @Test
    void tryLog_delegaEnLogYPersisteNormalmente() {
        when(auditLogRepository.findTopByOrderByCreatedAtDesc()).thenReturn(Optional.empty());

        service.tryLog(context(null, null));

        verify(auditLogRepository).save(any(SystemAuditLog.class));
    }

    @Test
    void tryLog_siLogLanzaExcepcion_laCapturaYNoLaPropaga() {
        org.mockito.Mockito.doThrow(new RuntimeException("fallo simulado de BD"))
                .when(auditLogRepository).acquireAuditChainLock();

        service.tryLog(context(null, null));
        // Si no lanzó, el catch de tryLog() funcionó correctamente.
    }

    // ── verifyChainIntegrity() ───────────────────────────────────────────────────

    @Test
    void verifyChainIntegrity_sinRegistros_devuelveTrue() {
        when(auditLogRepository.findAllByOrderByCreatedAtAsc()).thenReturn(List.of());

        assertThat(service.verifyChainIntegrity()).isTrue();
    }

    @Test
    void verifyChainIntegrity_cadenaValida_devuelveTrue() {
        SystemAuditLog log1 = captureLoggedEntry(Optional.empty(), context(null, Map.of("active", true)));
        SystemAuditLog log2 = captureLoggedEntry(Optional.of(log1), context(null, Map.of("active", false)));

        when(auditLogRepository.findAllByOrderByCreatedAtAsc()).thenReturn(List.of(log1, log2));

        assertThat(service.verifyChainIntegrity()).isTrue();
    }

    @Test
    void verifyChainIntegrity_previousLogHashRoto_devuelveFalse() {
        SystemAuditLog log1 = captureLoggedEntry(Optional.empty(), context(null, Map.of("active", true)));
        SystemAuditLog log2 = captureLoggedEntry(Optional.of(log1), context(null, Map.of("active", false)));
        log2.setPreviousLogHash("un-hash-que-no-corresponde");

        when(auditLogRepository.findAllByOrderByCreatedAtAsc()).thenReturn(List.of(log1, log2));

        assertThat(service.verifyChainIntegrity()).isFalse();
    }

    @Test
    void verifyChainIntegrity_contenidoAlteradoDespuesDeCalcularElHash_devuelveFalse() {
        SystemAuditLog log1 = captureLoggedEntry(Optional.empty(), context(null, Map.of("active", true)));
        // Alterar el actorId después de que el logHash ya fue calculado y guardado —
        // el hash recalculado ya no coincide con el almacenado.
        log1.setActorId("actor-suplantado");

        when(auditLogRepository.findAllByOrderByCreatedAtAsc()).thenReturn(List.of(log1));

        assertThat(service.verifyChainIntegrity()).isFalse();
    }

    private SystemAuditLog captureLoggedEntry(Optional<SystemAuditLog> previous, AuditContext ctx) {
        when(auditLogRepository.findTopByOrderByCreatedAtDesc()).thenReturn(previous);
        service.log(ctx);
        ArgumentCaptor<SystemAuditLog> captor = ArgumentCaptor.forClass(SystemAuditLog.class);
        verify(auditLogRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }
}
