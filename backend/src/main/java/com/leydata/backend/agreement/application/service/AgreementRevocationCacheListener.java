package com.leydata.backend.agreement.application.service;

import com.leydata.backend.agreement.domain.event.AgreementRevokedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgreementRevocationCacheListener {

    private final StringRedisTemplate redisTemplate;

    /**
     * Se ejecuta SOLO después del commit exitoso de Postgres.
     * Si Redis falla, la revocación legal ya está persistida — se loguea WARN y no se hace rollback.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAgreementRevoked(AgreementRevokedEvent event) {
        for (var purposeId : event.purposeIds()) {
            String key = "consent:" + event.subjectIdentifier() + ":" + purposeId;
            try {
                redisTemplate.delete(key);
                log.info("Redis: clave revocada {}", key);
            } catch (Exception e) {
                log.warn("Redis: no se pudo eliminar la clave {} tras revocación. " +
                         "La revocación legal está persistida en Postgres. Causa: {}", key, e.getMessage());
            }
        }
    }
}
