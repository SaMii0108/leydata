package com.leydata.backend.agreement.application.service;

import com.leydata.backend.agreement.application.dto.AgreementIntegrityLogResponse;
import com.leydata.backend.agreement.domain.event.AgreementIntegrityFailedEvent;
import com.leydata.backend.agreement.infrastructure.persistence.AgreementsRepository;
import com.leydata.backend.entity.Agreements;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgreementIntegrityScheduler {

    private final AgreementsRepository agreementsRepo;
    private final AgreementService agreementService;
    private final ApplicationEventPublisher eventPublisher;

    @Scheduled(cron = "0 0 3 * * *")
    public void verifyAllAgreements() {
        for (Agreements agreement : agreementsRepo.findAll()) {
            AgreementIntegrityLogResponse result;
            try {
                result = agreementService.verifyIntegrity(agreement.getId(), "SCHEDULED", null);
            } catch (Exception e) {
                log.error("Error verificando integridad del agreement {}: {}", agreement.getId(), e.getMessage());
                continue;
            }

            if (!Boolean.TRUE.equals(result.getIsValid())) {
                eventPublisher.publishEvent(new AgreementIntegrityFailedEvent(
                        agreement.getId(), result.getStoredHash(), result.getRecalculatedHash()));
            }
        }
    }
}
