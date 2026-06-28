package com.leydata.backend.agreement.application.service;

import com.leydata.backend.agreement.application.dto.*;
import com.leydata.backend.agreement.domain.exception.AgreementNotFoundException;
import com.leydata.backend.agreement.infrastructure.persistence.AgreementIntegrityLogRepository;
import com.leydata.backend.agreement.infrastructure.persistence.AgreementMetadataRepository;
import com.leydata.backend.agreement.infrastructure.persistence.AgreementsPurposesRepository;
import com.leydata.backend.agreement.infrastructure.persistence.AgreementsRepository;
import com.leydata.backend.audit.application.dto.AuditContext;
import com.leydata.backend.audit.application.service.AuditService;
import com.leydata.backend.entity.*;
import com.leydata.backend.privacydoc.domain.exception.BusinessValidationException;
import com.leydata.backend.privacydoc.infrastructure.persistence.DocumentPurposesRepository;
import com.leydata.backend.privacydoc.infrastructure.persistence.PrivacyDocumentsRepository;
import com.leydata.backend.purposes.infrastructure.persistence.PurposesRepository;
import com.leydata.backend.repository.DataSubjectsRepository;
import com.leydata.backend.shared.SecurityContextHelper;
import com.leydata.backend.template.infrastructure.persistence.TemplatePurposesRepository;
import com.leydata.backend.template.infrastructure.persistence.TemplatesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AgreementService {

    private final AgreementsRepository agreementsRepo;
    private final AgreementsPurposesRepository agreementsPurposesRepo;
    private final AgreementMetadataRepository agreementMetadataRepo;
    private final AgreementIntegrityLogRepository integrityLogRepo;

    private final DataSubjectsRepository dataSubjectsRepo;
    private final TemplatesRepository templatesRepo;
    private final TemplatePurposesRepository templatePurposesRepo;
    private final PrivacyDocumentsRepository privacyDocumentsRepo;
    private final DocumentPurposesRepository documentPurposesRepo;
    private final PurposesRepository purposesRepo;

    private final AuditService auditService;
    private final SecurityContextHelper securityContextHelper;

    // ── CREACIÓN ─────────────────────────────────────────────────────────────────

    public AgreementResponse create(CreateAgreementRequest req, String ipOrigin, String userAgent) {

        DataSubjects dataSubject = dataSubjectsRepo.findById(req.getDataSubjectId())
                .orElseThrow(() -> new BusinessValidationException(
                        "El data subject " + req.getDataSubjectId() + " no existe"));

        Templates template = templatesRepo.findById(req.getTemplateId())
                .orElseThrow(() -> new BusinessValidationException(
                        "El template " + req.getTemplateId() + " no existe"));
        // Regla 2: solo se puede consentir sobre un template ACTIVE
        if (!Boolean.TRUE.equals(template.getIsActive())) {
            throw new BusinessValidationException(
                    "Solo se puede crear un agreement contra un template ACTIVE");
        }

        PrivacyDocuments document = privacyDocumentsRepo.findById(req.getDocumentId())
                .orElseThrow(() -> new BusinessValidationException(
                        "El documento " + req.getDocumentId() + " no existe"));

        // Regla 4: el set de purposeId del request debe coincidir exactamente con las purposes
        // visibles del template (ni falta ni sobra ninguna)
        List<TemplatePurposes> visiblePurposes = templatePurposesRepo
                .findByTemplate_IdOrderByOrderPosition(template.getId())
                .stream()
                .filter(tp -> Boolean.TRUE.equals(tp.getIsVisible()))
                .toList();

        Set<UUID> visiblePurposeIds = visiblePurposes.stream()
                .map(tp -> tp.getPurpose().getId())
                .collect(Collectors.toSet());
        Set<UUID> requestPurposeIds = req.getPurposes().stream()
                .map(PurposeDecisionRequest::getPurposeId)
                .collect(Collectors.toSet());

        if (!visiblePurposeIds.equals(requestPurposeIds)) {
            throw new BusinessValidationException(
                    "Las purposes del request no coinciden con las purposes visibles del template");
        }

        // Regla 16: el documento debe cubrir (vía DOCUMENT_PURPOSES) todas las purposes del request
        for (UUID purposeId : requestPurposeIds) {
            if (!documentPurposesRepo.existsByDocument_IdAndPurpose_IdAndIsActiveTrue(document.getId(), purposeId)) {
                throw new BusinessValidationException(
                        "El documento " + document.getId() + " no cubre la purpose " + purposeId);
            }
        }

        // Regla 5: si la purpose es required, no puede aceptarse=false
        for (PurposeDecisionRequest decision : req.getPurposes()) {
            Purposes purpose = purposesRepo.findById(decision.getPurposeId())
                    .orElseThrow(() -> new BusinessValidationException(
                            "La purpose " + decision.getPurposeId() + " no existe"));

            if (Boolean.TRUE.equals(purpose.getRequired()) && Boolean.FALSE.equals(decision.getAccepted())) {
                throw new BusinessValidationException(
                        "La purpose " + purpose.getCode() + " es required y no puede rechazarse");
            }
        }

        String actorId = resolveActorIdOrNull();

        // Regla 15: si ya hay un ACTIVE para este (dataSubject, template), se cierra antes de crear el nuevo
        UUID previousAgreementsId = agreementsRepo
                .findByDataSubjectIdAndTemplateIdAndStatus(req.getDataSubjectId(), req.getTemplateId(), "ACTIVE")
                .map(previous -> {
                    closeActiveAgreementForReconsent(previous, actorId);
                    return previous.getId();
                })
                .orElse(null);

        LocalDateTime now = LocalDateTime.now();

        Agreements agreement = new Agreements();
        agreement.setDataSubjectId(dataSubject.getId());
        agreement.setTemplateId(template.getId());
        agreement.setTemplateVersion(template.getVersion());
        agreement.setDocumentId(document.getId());
        agreement.setStatus("ACTIVE");
        agreement.setPreviousAgreementsId(previousAgreementsId);
        agreement.setCreatedAt(now);
        Agreements savedAgreement = agreementsRepo.save(agreement);

        List<AgreementsPurposes> savedPurposes = new ArrayList<>();
        for (PurposeDecisionRequest decision : req.getPurposes()) {
            Purposes purpose = purposesRepo.findById(decision.getPurposeId())
                    .orElseThrow(() -> new BusinessValidationException(
                            "La purpose " + decision.getPurposeId() + " no existe"));

            AgreementsPurposes ap = new AgreementsPurposes();
            ap.setAgreementId(savedAgreement.getId());
            ap.setPurposeId(decision.getPurposeId());
            ap.setAccepted(decision.getAccepted());
            ap.setPurposeCode(purpose.getCode());
            ap.setPurposeName(purpose.getName());
            ap.setPurposeDescription(purpose.getDescription());
            ap.setPurposeShortDescription(purpose.getShortDescription());
            ap.setPurposeRequired(purpose.getRequired());
            ap.setPurposeRevocable(purpose.getRevocable());
            ap.setPurposeHash(purpose.getHashSha256()); // Regla 7
            ap.setLegalBasisCode(purpose.getLegalBasis() != null ? purpose.getLegalBasis().getCode() : null);
            ap.setStatus("ACTIVE");
            ap.setExpiresAt(null); // Regla 6.1: pendiente del futuro cálculo por política de retención
            ap.setCreatedAt(now);

            // Cadena de hash propia de AGREEMENTS_PURPOSES (Regla 17)
            String previousApHash = agreementsPurposesRepo.findTopByOrderByCreatedAtDesc()
                    .map(AgreementsPurposes::getHashSha256)
                    .orElse(null);
            ap.setPreviousHashSha256(previousApHash);
            ap.setHashSha256(computePurposeRowHash(ap));

            savedPurposes.add(agreementsPurposesRepo.save(ap));
        }

        AgreementMetadata metadata = new AgreementMetadata();
        metadata.setAgreementId(savedAgreement.getId());
        metadata.setIpOrigin(ipOrigin);
        metadata.setUserAgent(userAgent);
        if (req.getMetadata() != null) {
            metadata.setCaptureChannel(req.getMetadata().getCaptureChannel());
            metadata.setSignatureToken(req.getMetadata().getSignatureToken());
            metadata.setAuthProvider(req.getMetadata().getAuthProvider());
            metadata.setExtraVariables(req.getMetadata().getExtraVariables());
        }
        metadata.setCreatedAt(now);
        AgreementMetadata savedMetadata = agreementMetadataRepo.save(metadata);

        // Regla 10: hash combinado AGREEMENTS + AGREEMENTS_PURPOSES + AGREEMENT_METADATA
        String previousAgreementHash = agreementsRepo.findTopByOrderByCreatedAtDesc()
                .filter(a -> !a.getId().equals(savedAgreement.getId()))
                .map(Agreements::getHashSha256)
                .orElse(null);
        savedAgreement.setPreviousHashSha256(previousAgreementHash);
        savedAgreement.setHashSha256(computeAgreementHash(savedAgreement, savedPurposes, savedMetadata));
        Agreements finalAgreement = agreementsRepo.save(savedAgreement);

        return AgreementResponse.from(
                finalAgreement,
                savedPurposes.stream().map(AgreementPurposeResponse::from).toList(),
                AgreementMetadataResponse.from(savedMetadata));
    }

    private void closeActiveAgreementForReconsent(Agreements previous, String actorId) {
        previous.setStatus("REVOKED");
        agreementsRepo.save(previous);

        // Regla 6.1: REVOKED se hereda a las AGREEMENTS_PURPOSES del agreement cerrado
        List<AgreementsPurposes> previousPurposes = agreementsPurposesRepo.findByAgreementId(previous.getId());
        previousPurposes.forEach(ap -> ap.setStatus("REVOKED"));
        agreementsPurposesRepo.saveAll(previousPurposes);

        auditService.log(AuditContext.builder()
                .tableName("agreements")
                .recordId(previous.getId())
                .action("REVOCAR_AGREEMENT_POR_RECONSENTIMIENTO")
                .oldData(Map.of("status", "ACTIVE"))
                .newData(Map.of("status", "REVOKED"))
                .actorId(actorId)
                .actorRole(actorId != null ? securityContextHelper.getActorRole() : "SYSTEM")
                .build());
    }

    // ── CONSULTA ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AgreementResponse getById(UUID id) {
        Agreements agreement = findOrThrow(id);
        return toResponse(agreement);
    }

    @Transactional(readOnly = true)
    public Optional<AgreementResponse> getActive(UUID dataSubjectId, UUID templateId) {
        return agreementsRepo.findByDataSubjectIdAndTemplateIdAndStatus(dataSubjectId, templateId, "ACTIVE")
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<AgreementResponse> list(UUID dataSubjectId, UUID templateId, String status) {
        List<Agreements> base;
        if (dataSubjectId != null) {
            base = agreementsRepo.findByDataSubjectIdOrderByCreatedAtDesc(dataSubjectId);
        } else if (templateId != null) {
            base = agreementsRepo.findByTemplateId(templateId);
        } else if (status != null) {
            base = agreementsRepo.findByStatus(status);
        } else {
            base = agreementsRepo.findAll();
        }

        return base.stream()
                .filter(a -> templateId == null || templateId.equals(a.getTemplateId()))
                .filter(a -> status == null || status.equals(a.getStatus()))
                .filter(a -> dataSubjectId == null || dataSubjectId.equals(a.getDataSubjectId()))
                .map(this::toResponse)
                .toList();
    }

    // ── INTEGRIDAD ───────────────────────────────────────────────────────────────

    public AgreementIntegrityLogResponse verifyIntegrity(UUID agreementId, String checkType, UUID actorId) {
        Agreements agreement = findOrThrow(agreementId);
        List<AgreementsPurposes> purposes = agreementsPurposesRepo.findByAgreementId(agreementId);
        AgreementMetadata metadata = agreementMetadataRepo.findByAgreementId(agreementId).orElse(null);

        String storedHash = agreement.getHashSha256();
        String recalculatedHash = computeAgreementHash(agreement, purposes, metadata);
        boolean isValid = Objects.equals(storedHash, recalculatedHash);

        AgreementIntegrityLog log = new AgreementIntegrityLog();
        log.setAgreementId(agreementId);
        log.setStoredHash(storedHash);
        log.setRecalculatedHash(recalculatedHash);
        log.setIsValid(isValid);
        log.setCheckType(checkType);
        log.setCreatedAt(LocalDateTime.now());
        log.setErrorDetail(isValid ? null : "El hash recalculado no coincide con el almacenado");
        log.setCreatedBy(actorId);

        String previousLogHash = integrityLogRepo.findTopByOrderByCreatedAtDesc()
                .map(AgreementIntegrityLog::getHashSha256)
                .orElse(null);
        log.setPreviousHashSha256Id(previousLogHash);
        log.setHashSha256(computeIntegrityLogRowHash(log));

        return AgreementIntegrityLogResponse.from(integrityLogRepo.save(log));
    }

    @Transactional(readOnly = true)
    public List<AgreementIntegrityLogResponse> getIntegrityLog(UUID agreementId) {
        findOrThrow(agreementId);
        return integrityLogRepo.findByAgreementIdOrderByCreatedAtDesc(agreementId)
                .stream().map(AgreementIntegrityLogResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AgreementIntegrityLogResponse> listFailedVerifications() {
        return integrityLogRepo.findByIsValidFalse()
                .stream().map(AgreementIntegrityLogResponse::from).toList();
    }

    // ── HASHING ──────────────────────────────────────────────────────────────────

    /** Regla 10: hash combinado de AGREEMENTS + AGREEMENTS_PURPOSES + AGREEMENT_METADATA. */
    private String computeAgreementHash(Agreements a, List<AgreementsPurposes> purposes, AgreementMetadata metadata) {
        StringBuilder sb = new StringBuilder()
                .append(a.getDataSubjectId()).append("|")
                .append(a.getTemplateId()).append("|")
                .append(a.getTemplateVersion()).append("|")
                .append(a.getDocumentId()).append("|")
                .append(a.getStatus()).append("|")
                .append(a.getExpiration()).append("|")
                .append(a.getPreviousAgreementsId()).append("|")
                .append(a.getCreatedAt());

        for (AgreementsPurposes ap : purposes) {
            sb.append("||")
              .append(ap.getPurposeId()).append(":")
              .append(ap.getAccepted()).append(":")
              .append(ap.getPurposeCode()).append(":")
              .append(ap.getPurposeName()).append(":")
              .append(ap.getPurposeDescription()).append(":")
              .append(ap.getPurposeShortDescription()).append(":")
              .append(ap.getPurposeRequired()).append(":")
              .append(ap.getPurposeRevocable()).append(":")
              .append(ap.getPurposeHash()).append(":")
              .append(ap.getLegalBasisCode()).append(":")
              .append(ap.getStatus()).append(":")
              .append(ap.getExpiresAt()).append(":")
              .append(ap.getCreatedAt());
        }

        if (metadata != null) {
            // Regla 10: EXTRA_VARIABLES y CREATED_AT de metadata quedan fuera del hash
            sb.append("||")
              .append(metadata.getIpOrigin()).append(":")
              .append(metadata.getUserAgent()).append(":")
              .append(metadata.getCaptureChannel()).append(":")
              .append(metadata.getSignatureToken()).append(":")
              .append(metadata.getAuthProvider());
        }

        return sha256(sb.toString());
    }

    /** Regla 17: hash propio de cada fila de AGREEMENTS_PURPOSES, para su cadena de integridad. */
    private String computePurposeRowHash(AgreementsPurposes ap) {
        String content = String.join("|",
                String.valueOf(ap.getAgreementId()),
                String.valueOf(ap.getPurposeId()),
                String.valueOf(ap.getAccepted()),
                String.valueOf(ap.getPurposeHash()),
                String.valueOf(ap.getCreatedAt()));
        return sha256(content);
    }

    private String computeIntegrityLogRowHash(AgreementIntegrityLog log) {
        String content = String.join("|",
                String.valueOf(log.getAgreementId()),
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

    // ── HELPERS ──────────────────────────────────────────────────────────────────

    private AgreementResponse toResponse(Agreements agreement) {
        List<AgreementPurposeResponse> purposes = agreementsPurposesRepo.findByAgreementId(agreement.getId())
                .stream().map(AgreementPurposeResponse::from).toList();
        AgreementMetadataResponse metadata = agreementMetadataRepo.findByAgreementId(agreement.getId())
                .map(AgreementMetadataResponse::from)
                .orElse(null);
        return AgreementResponse.from(agreement, purposes, metadata);
    }

    private Agreements findOrThrow(UUID id) {
        return agreementsRepo.findById(id)
                .orElseThrow(() -> new AgreementNotFoundException(id));
    }

    /** La creación de un agreement no siempre la dispara un usuario autenticado (puede ser el futuro orquestador). */
    private String resolveActorIdOrNull() {
        try {
            return securityContextHelper.getKeycloakId();
        } catch (Exception e) {
            return null;
        }
    }
}
