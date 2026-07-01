package com.leydata.backend.audit.application.service;

import com.leydata.backend.agreement.infrastructure.persistence.AgreementsRepository;
import com.leydata.backend.privacydoc.infrastructure.persistence.PrivacyDocumentsRepository;
import com.leydata.backend.purposes.infrastructure.persistence.PurposesRepository;
import com.leydata.backend.template.infrastructure.persistence.TemplatesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Reemplaza a AgreementIntegrityScheduler — corre sobre las 4 entidades con hash propio.
@Slf4j
@Component
@RequiredArgsConstructor
public class IntegrityScheduler {

    private final IntegrityVerifier integrityVerifier;
    private final AgreementsRepository agreementsRepo;
    private final TemplatesRepository templatesRepo;
    private final PrivacyDocumentsRepository privacyDocumentsRepo;
    private final PurposesRepository purposesRepo;

    @Scheduled(cron = "0 0 3 * * *")
    public void verifyAllEntities() {
        agreementsRepo.findAll().forEach(a -> verifyOne("AGREEMENT", a.getId()));
        templatesRepo.findAll().forEach(t -> verifyOne("TEMPLATE", t.getId()));
        privacyDocumentsRepo.findAll().forEach(d -> verifyOne("DOCUMENT", d.getId()));
        purposesRepo.findAll().forEach(p -> verifyOne("PURPOSE", p.getId()));
    }

    private void verifyOne(String entityType, UUID entityId) {
        try {
            integrityVerifier.verify(entityType, entityId, "SCHEDULED", null);
        } catch (Exception e) {
            log.error("Error verificando integridad de {} {}: {}", entityType, entityId, e.getMessage());
        }
    }
}
