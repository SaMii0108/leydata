package com.leydata.backend.audit.application.service;

import com.leydata.backend.agreement.application.service.AgreementService;
import com.leydata.backend.agreement.domain.exception.AgreementNotFoundException;
import com.leydata.backend.agreement.infrastructure.persistence.AgreementsRepository;
import com.leydata.backend.audit.application.dto.EntityIntegrityLogResponse;
import com.leydata.backend.audit.infrastructure.persistence.EntityIntegrityLogRepository;
import com.leydata.backend.entity.EntityIntegrityLog;
import com.leydata.backend.privacydoc.application.service.PrivacyDocumentService;
import com.leydata.backend.privacydoc.domain.exception.BusinessValidationException;
import com.leydata.backend.privacydoc.domain.exception.DocumentNotFoundException;
import com.leydata.backend.privacydoc.infrastructure.persistence.PrivacyDocumentsRepository;
import com.leydata.backend.purposes.application.service.PurposeService;
import com.leydata.backend.purposes.domain.exception.PurposeNotFoundException;
import com.leydata.backend.purposes.infrastructure.persistence.PurposesRepository;
import com.leydata.backend.template.application.service.TemplateService;
import com.leydata.backend.template.domain.exception.TemplateNotFoundException;
import com.leydata.backend.template.infrastructure.persistence.TemplatesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

// Verificación de integridad genérica sobre las 4 entidades que tienen hash propio.
// Reemplaza la lógica que vivía duplicada en AgreementService.verifyIntegrity().
@Service
@RequiredArgsConstructor
public class IntegrityVerifier {

    private final EntityIntegrityLogRepository integrityLogRepo;

    private final AgreementsRepository agreementsRepo;
    private final TemplatesRepository templatesRepo;
    private final PrivacyDocumentsRepository privacyDocumentsRepo;
    private final PurposesRepository purposesRepo;

    private final AgreementService agreementService;
    private final TemplateService templateService;
    private final PrivacyDocumentService privacyDocumentService;
    private final PurposeService purposeService;

    @Transactional
    public EntityIntegrityLogResponse verify(String entityType, UUID entityId, String checkType, String actorId) {
        String storedHash = readStoredHash(entityType, entityId);
        String recalculatedHash = recalculate(entityType, entityId);
        boolean isValid = Objects.equals(storedHash, recalculatedHash);

        EntityIntegrityLog log = new EntityIntegrityLog();
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setStoredHash(storedHash);
        log.setRecalculatedHash(recalculatedHash);
        log.setIsValid(isValid);
        log.setCheckType(checkType);
        log.setCreatedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
        log.setErrorDetail(isValid ? null : "El hash recalculado no coincide con el almacenado");
        log.setCreatedBy(actorId);

        String previousLogHash = integrityLogRepo.findTopByOrderByCreatedAtDesc()
                .map(EntityIntegrityLog::getHashSha256)
                .orElse("GENESIS");
        log.setPreviousHashSha256Id(previousLogHash);
        log.setHashSha256(computeLogRowHash(log));

        return EntityIntegrityLogResponse.from(integrityLogRepo.save(log));
    }

    private String readStoredHash(String entityType, UUID entityId) {
        return switch (entityType) {
            case "AGREEMENT" -> agreementsRepo.findById(entityId)
                    .orElseThrow(() -> new AgreementNotFoundException(entityId)).getHashSha256();
            case "TEMPLATE" -> templatesRepo.findById(entityId)
                    .orElseThrow(() -> new TemplateNotFoundException(entityId)).getHashSha256();
            case "DOCUMENT" -> privacyDocumentsRepo.findById(entityId)
                    .orElseThrow(() -> new DocumentNotFoundException(entityId)).getHashSha256();
            case "PURPOSE" -> purposesRepo.findById(entityId)
                    .orElseThrow(() -> new PurposeNotFoundException(entityId)).getHashSha256();
            default -> throw new BusinessValidationException("entityType inválido: " + entityType);
        };
    }

    private String recalculate(String entityType, UUID entityId) {
        return switch (entityType) {
            case "AGREEMENT" -> agreementService.recalculateHash(entityId);
            case "TEMPLATE" -> templateService.recalculateHash(entityId);
            case "DOCUMENT" -> privacyDocumentService.recalculateHash(entityId);
            case "PURPOSE" -> purposeService.recalculateHash(entityId);
            default -> throw new BusinessValidationException("entityType inválido: " + entityType);
        };
    }

    private String computeLogRowHash(EntityIntegrityLog log) {
        String content = String.join("|",
                log.getEntityType(),
                String.valueOf(log.getEntityId()),
                String.valueOf(log.getStoredHash()),
                String.valueOf(log.getRecalculatedHash()),
                String.valueOf(log.getIsValid()),
                String.valueOf(log.getCheckType()),
                String.valueOf(log.getCreatedAt()));
        return sha256(content);
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 no disponible en este JVM", e);
        }
    }
}
