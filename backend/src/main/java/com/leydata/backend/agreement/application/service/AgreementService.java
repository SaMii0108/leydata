package com.leydata.backend.agreement.application.service;

import com.leydata.backend.agreement.application.dto.*;
import com.leydata.backend.agreement.domain.event.AgreementRevokedEvent;
import com.leydata.backend.agreement.domain.exception.AgreementNotFoundException;
import com.leydata.backend.agreement.infrastructure.persistence.AgreementMetadataRepository;
import com.leydata.backend.agreement.infrastructure.persistence.AgreementsPurposesRepository;
import com.leydata.backend.agreement.infrastructure.persistence.AgreementsRepository;
import com.leydata.backend.audit.application.dto.AuditContext;
import com.leydata.backend.audit.application.service.AuditService;
import com.leydata.backend.entity.*;
import io.micrometer.core.instrument.MeterRegistry;
import com.leydata.backend.privacydoc.domain.enums.DocumentStatus;
import com.leydata.backend.privacydoc.domain.exception.BusinessValidationException;
import com.leydata.backend.privacydoc.infrastructure.persistence.DocumentPurposesRepository;
import com.leydata.backend.privacydoc.infrastructure.persistence.PrivacyDocumentsRepository;
import com.leydata.backend.purposedatacategory.infrastructure.persistence.PurposeDataCategoryRepository;
import com.leydata.backend.purposedatacategory.infrastructure.persistence.RetentionPolicyRepository;
import com.leydata.backend.purposes.infrastructure.persistence.PurposesRepository;
import com.leydata.backend.repository.DataSubjectsRepository;
import com.leydata.backend.shared.SecurityContextHelper;
import com.leydata.backend.template.infrastructure.persistence.TemplatePurposesRepository;
import com.leydata.backend.template.infrastructure.persistence.TemplatesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AgreementService {

    private final AgreementsRepository agreementsRepo;
    private final AgreementsPurposesRepository agreementsPurposesRepo;
    private final AgreementMetadataRepository agreementMetadataRepo;

    private final DataSubjectsRepository dataSubjectsRepo;
    private final TemplatesRepository templatesRepo;
    private final TemplatePurposesRepository templatePurposesRepo;
    private final PrivacyDocumentsRepository privacyDocumentsRepo;
    private final DocumentPurposesRepository documentPurposesRepo;
    private final PurposesRepository purposesRepo;
    private final PurposeDataCategoryRepository purposeDataCategoryRepo;
    private final RetentionPolicyRepository retentionPolicyRepo;

    private final AuditService auditService;
    private final SecurityContextHelper securityContextHelper;
    private final jakarta.persistence.EntityManager entityManager;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;

    // ── CREACIÓN ─────────────────────────────────────────────────────────────────

    public AgreementResponse create(CreateAgreementRequest req, String ipOrigin, String userAgent) {

        // Postgres normaliza el valor al guardarlo en una columna inet (ej: "0:0:0:0:0:0:0:1" -> "::1").
        // Se normaliza acá para que el hash calculado en memoria coincida con el que se relee de la BD.
        ipOrigin = normalizeIp(ipOrigin);

        DataSubjects dataSubject;
        if (req.getDataSubjectId() != null) {
            dataSubject = dataSubjectsRepo.findById(req.getDataSubjectId())
                    .orElseThrow(() -> new BusinessValidationException(
                            "El data subject " + req.getDataSubjectId() + " no existe"));
        } else if (req.getSubjectIdentifier() != null && !req.getSubjectIdentifier().isBlank()) {
            dataSubject = dataSubjectsRepo.findByIdentifier(req.getSubjectIdentifier())
                    .orElseGet(() -> {
                        DataSubjects s = new DataSubjects();
                        s.setIdentifier(req.getSubjectIdentifier());
                        s.setCreatedAt(java.time.LocalDateTime.now());
                        return dataSubjectsRepo.save(s);
                    });
        } else {
            throw new BusinessValidationException("Se requiere dataSubjectId o subjectIdentifier");
        }

        Templates template = templatesRepo.findById(req.getTemplateId())
                .orElseThrow(() -> new BusinessValidationException(
                        "El template " + req.getTemplateId() + " no existe"));
        // Regla 2: solo se puede consentir sobre un template ACTIVE
        if (!Boolean.TRUE.equals(template.getIsActive())) {
            throw new BusinessValidationException(
                    "Solo se puede crear un agreement contra un template ACTIVE");
        }

        PrivacyDocuments document;
        if (req.getDocumentId() != null) {
            document = privacyDocumentsRepo.findById(req.getDocumentId())
                    .orElseThrow(() -> new BusinessValidationException(
                            "El documento " + req.getDocumentId() + " no existe"));
        } else {
            document = privacyDocumentsRepo
                    .findByTemplateIdAndStatusAndIsActiveTrue(template.getId(), DocumentStatus.PUBLISHED)
                    .orElseThrow(() -> new BusinessValidationException(
                            "El template " + template.getId() + " no tiene un documento publicado asociado"));
        }

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

        // Truncado a microsegundos: la columna es timestamp(6); sin esto, el hash
        // calculado en memoria (nanosegundos) nunca coincide con el valor releído de la BD.
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);

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
            ap.setExpiresAt(calculateExpiresAt(decision.getPurposeId(), now));
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
        // Postgres normaliza ip_origin (columna inet) al guardar (ej: forma larga de IPv6 -> forma corta).
        // Se relee tras el flush para que el hash use el mismo valor que se obtendrá en futuras verificaciones.
        agreementMetadataRepo.flush();
        entityManager.refresh(savedMetadata);

        // Regla 10: hash combinado AGREEMENTS + AGREEMENTS_PURPOSES + AGREEMENT_METADATA
        String previousAgreementHash = agreementsRepo.findTopByOrderByCreatedAtDesc()
                .filter(a -> !a.getId().equals(savedAgreement.getId()))
                .map(Agreements::getHashSha256)
                .orElse(null);
        savedAgreement.setPreviousHashSha256(previousAgreementHash);
        savedAgreement.setHashSha256(computeAgreementHash(savedAgreement, savedPurposes, savedMetadata));
        Agreements finalAgreement = agreementsRepo.save(savedAgreement);

        meterRegistry.counter("consent.captured",
                "template", template.getId().toString()).increment();
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

    // ── REVOCACIÓN EXPLÍCITA (llamada por el Orquestador) ────────────────────────

    @Transactional
    public AgreementResponse revoke(UUID agreementId, String subjectId, String realIp) {
        Agreements agreement = findOrThrow(agreementId);

        if (!"ACTIVE".equals(agreement.getStatus())) {
            throw new IllegalStateException("Solo se puede revocar un agreement en estado ACTIVE");
        }

        // Validar que el agreement pertenece al subjectId opaco enviado por el Orquestador
        DataSubjects dataSubject = dataSubjectsRepo.findById(agreement.getDataSubjectId())
                .orElseThrow(() -> new IllegalStateException("DataSubject no encontrado para este agreement"));
        if (!dataSubject.getIdentifier().equals(subjectId)) {
            throw new IllegalStateException("El agreement no pertenece al subjectId indicado");
        }

        agreement.setStatus("REVOKED");
        agreementsRepo.save(agreement);

        List<AgreementsPurposes> purposes = agreementsPurposesRepo.findByAgreementId(agreementId);
        purposes.forEach(ap -> ap.setStatus("REVOKED"));
        agreementsPurposesRepo.saveAll(purposes);

        String actorId = resolveActorIdOrNull();
        auditService.log(AuditContext.builder()
                .tableName("agreements")
                .recordId(agreementId)
                .action("REVOCAR_AGREEMENT")
                .oldData(Map.of("status", "ACTIVE"))
                .newData(Map.of("status", "REVOKED", "revokedBySubject", subjectId, "ipAddress", realIp != null ? realIp : "unknown"))
                .actorId(actorId)
                .actorRole(actorId != null ? securityContextHelper.getActorRole() : "ORCHESTRATOR")
                .build());

        meterRegistry.counter("consent.revoked",
                "template", agreement.getTemplateId().toString()).increment();
        // Publica el evento — el listener elimina Redis DESPUÉS del commit (AFTER_COMMIT)
        List<UUID> purposeIds = purposes.stream().map(AgreementsPurposes::getPurposeId).toList();
        eventPublisher.publishEvent(new AgreementRevokedEvent(dataSubject.getIdentifier(), purposeIds));

        return toResponse(agreement);
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

    // ── CICLO DE VIDA (para el Orquestador) ──────────────────────────────────────

    /**
     * CHECK con estado completo del ciclo de vida: ALLOWED, EXPIRED, REQUIRES_RECONSENT, PENDING.
     * Usa subjectIdentifier (string opaco del CRM) y templateKey + domainId.
     */
    @Transactional(readOnly = true)
    public ConsentLifecycleResponse getLifecycleStatus(String subjectIdentifier, UUID domainId, String templateKey) {
        Templates currentTemplate = templatesRepo
                .findByDomainIdAndTemplateKeyAndIsActiveTrue(domainId, templateKey.toUpperCase())
                .orElseThrow(() -> new BusinessValidationException(
                        "No hay una versión activa para el template " + templateKey + " en este dominio"));

        Optional<Agreements> agreementOpt = agreementsRepo
                .findActiveBySubjectIdentifierAndTemplateId(subjectIdentifier, currentTemplate.getId());

        // Si el acuerdo existía en una versión anterior (ya fue re-consented y el nuevo es el activo)
        // buscamos también en templates anteriores del mismo key
        if (agreementOpt.isEmpty()) {
            // Buscar en versiones anteriores del mismo templateKey para detectar REQUIRES_RECONSENT
            List<Templates> allVersions = templatesRepo
                    .findByDomainIdAndTemplateKeyOrderByVersionDesc(domainId, templateKey.toUpperCase());
            for (Templates olderTemplate : allVersions) {
                if (olderTemplate.getId().equals(currentTemplate.getId())) continue;
                Optional<Agreements> older = agreementsRepo
                        .findActiveBySubjectIdentifierAndTemplateId(subjectIdentifier, olderTemplate.getId());
                if (older.isPresent()) {
                    agreementOpt = older;
                    break;
                }
            }
        }

        if (agreementOpt.isEmpty()) {
            return ConsentLifecycleResponse.builder()
                    .subjectIdentifier(subjectIdentifier)
                    .templateKey(templateKey)
                    .status("PENDING")
                    .currentTemplateVersion(currentTemplate.getVersion())
                    .build();
        }

        Agreements agreement = agreementOpt.get();
        List<AgreementsPurposes> purposes = agreementsPurposesRepo.findByAgreementId(agreement.getId());
        LocalDateTime now = LocalDateTime.now();

        // REQUIRES_RECONSENT: template activo es más nuevo Y tiene forceReconsent=true
        boolean requiresReconsent = Boolean.TRUE.equals(currentTemplate.getForceReconsent())
                && agreement.getTemplateVersion() < currentTemplate.getVersion();

        if (requiresReconsent) {
            return ConsentLifecycleResponse.builder()
                    .subjectIdentifier(subjectIdentifier)
                    .templateKey(templateKey)
                    .status("REQUIRES_RECONSENT")
                    .agreementId(agreement.getId())
                    .agreementTemplateVersion(agreement.getTemplateVersion())
                    .currentTemplateVersion(currentTemplate.getVersion())
                    .build();
        }

        // EXPIRED: alguna purpose ACTIVE tiene expiresAt en el pasado
        LocalDateTime earliestExpiry = purposes.stream()
                .filter(p -> "ACTIVE".equals(p.getStatus()) && p.getExpiresAt() != null)
                .map(AgreementsPurposes::getExpiresAt)
                .min(Comparator.naturalOrder())
                .orElse(null);

        if (earliestExpiry != null && earliestExpiry.isBefore(now)) {
            return ConsentLifecycleResponse.builder()
                    .subjectIdentifier(subjectIdentifier)
                    .templateKey(templateKey)
                    .status("EXPIRED")
                    .agreementId(agreement.getId())
                    .agreementTemplateVersion(agreement.getTemplateVersion())
                    .currentTemplateVersion(currentTemplate.getVersion())
                    .earliestExpiresAt(earliestExpiry)
                    .build();
        }

        return ConsentLifecycleResponse.builder()
                .subjectIdentifier(subjectIdentifier)
                .templateKey(templateKey)
                .status("ALLOWED")
                .agreementId(agreement.getId())
                .agreementTemplateVersion(agreement.getTemplateVersion())
                .currentTemplateVersion(currentTemplate.getVersion())
                .earliestExpiresAt(earliestExpiry)
                .build();
    }

    /**
     * Resumen de estado de todas las purposes de un titular en un dominio.
     * Usado por el portal del titular para pintar los switches.
     */
    @Transactional(readOnly = true)
    public List<SubjectSummaryResponse> getSubjectSummary(String subjectIdentifier, UUID domainId) {
        List<Agreements> agreements = agreementsRepo
                .findActiveBySubjectIdentifierAndDomainId(subjectIdentifier, domainId);

        LocalDateTime now = LocalDateTime.now();

        return agreements.stream().map(agreement -> {
            List<AgreementsPurposes> purposes = agreementsPurposesRepo.findByAgreementId(agreement.getId());
            Templates template = templatesRepo.findById(agreement.getTemplateId()).orElse(null);

            List<PurposeSummaryItem> purposeItems = purposes.stream().map(ap -> {
                String purposeStatus = ap.getExpiresAt() != null && ap.getExpiresAt().isBefore(now)
                        ? "EXPIRED"
                        : ap.getStatus();
                return PurposeSummaryItem.builder()
                        .purposeId(ap.getPurposeId())
                        .purposeCode(ap.getPurposeCode())
                        .purposeName(ap.getPurposeName())
                        .accepted(Boolean.TRUE.equals(ap.getAccepted()))
                        .status(purposeStatus)
                        .expiresAt(ap.getExpiresAt())
                        .acceptedAt(ap.getCreatedAt())
                        .required(Boolean.TRUE.equals(ap.getPurposeRequired()))
                        .revocable(Boolean.TRUE.equals(ap.getPurposeRevocable()))
                        .build();
            }).toList();

            return SubjectSummaryResponse.builder()
                    .subjectIdentifier(subjectIdentifier)
                    .domainId(domainId)
                    .agreementId(agreement.getId())
                    .templateId(agreement.getTemplateId())
                    .templateKey(template != null ? template.getTemplateKey() : null)
                    .templateVersion(agreement.getTemplateVersion())
                    .documentId(agreement.getDocumentId())
                    .purposes(purposeItems)
                    .build();
        }).toList();
    }

    /**
     * Lista de purposes vencidas pendientes de eliminación de datos en el CRM.
     * Cubre el deber de supresión de la Ley 21.719.
     */
    @Transactional(readOnly = true)
    public List<PendingDeletionItem> getPendingDeletions(UUID domainId) {
        LocalDateTime now = LocalDateTime.now();
        List<AgreementsPurposes> expired = agreementsPurposesRepo.findExpiredByDomainId(domainId, now);

        return expired.stream().map(ap -> {
            Agreements agreement = agreementsRepo.findById(ap.getAgreementId()).orElse(null);
            DataSubjects subject = agreement != null
                    ? dataSubjectsRepo.findById(agreement.getDataSubjectId()).orElse(null)
                    : null;

            // Resolver anonymizeAfter desde la política de retención de la purpose
            boolean anonymize = purposeDataCategoryRepo.findByPurposeId(ap.getPurposeId()).stream()
                    .anyMatch(pdc -> {
                        var policy = retentionPolicyRepo.findByPurposeDataCategoryId(pdc.getId());
                        return policy.map(p -> Boolean.TRUE.equals(p.getAnonymizeAfter())).orElse(false);
                    });

            return PendingDeletionItem.builder()
                    .subjectIdentifier(subject != null ? subject.getIdentifier() : null)
                    .purposeId(ap.getPurposeId())
                    .purposeCode(ap.getPurposeCode())
                    .purposeName(ap.getPurposeName())
                    .agreementId(ap.getAgreementId())
                    .expiredAt(ap.getExpiresAt())
                    .anonymizeAfter(anonymize)
                    .build();
        }).toList();
    }

    /**
     * El CRM confirma que eliminó los datos del titular para una purpose vencida.
     * Marca la fila como EXPIRED y registra en auditoría (evidencia ante fiscalización).
     */
    @Transactional
    public void confirmDeletion(ConfirmDeletionRequest req) {
        DataSubjects subject = dataSubjectsRepo.findByIdentifier(req.getSubjectIdentifier())
                .orElseThrow(() -> new BusinessValidationException(
                        "Titular no encontrado: " + req.getSubjectIdentifier()));

        // Buscar todos los acuerdos ACTIVE del titular y encontrar la purpose correcta
        List<Agreements> agreements = agreementsRepo.findByDataSubjectIdOrderByCreatedAtDesc(subject.getId());
        AgreementsPurposes target = agreements.stream()
                .flatMap(a -> agreementsPurposesRepo.findByAgreementId(a.getId()).stream())
                .filter(ap -> req.getPurposeId().equals(ap.getPurposeId()) && "ACTIVE".equals(ap.getStatus()))
                .findFirst()
                .orElseThrow(() -> new BusinessValidationException(
                        "No se encontró purpose activa para eliminar: " + req.getPurposeId()));

        target.setStatus("EXPIRED");
        agreementsPurposesRepo.save(target);

        LocalDateTime deletedAt = req.getDeletedAt() != null ? req.getDeletedAt() : LocalDateTime.now();
        auditService.log(AuditContext.builder()
                .tableName("agreements_purposes")
                .recordId(target.getId())
                .action("CONFIRMAR_ELIMINACION_DATO")
                .oldData(Map.of("status", "ACTIVE"))
                .newData(Map.of(
                        "status",      "EXPIRED",
                        "purposeCode", target.getPurposeCode(),
                        "deletedAt",   deletedAt.toString(),
                        "subjectId",   req.getSubjectIdentifier()))
                .actorId(resolveActorIdOrNull())
                .actorRole("ORCHESTRATOR")
                .build());
    }

    // ── INTEGRIDAD ───────────────────────────────────────────────────────────────

    /** Recalcula el hash combinado sin escribir log — usado por IntegrityVerifier. */
    @Transactional(readOnly = true)
    public String recalculateHash(UUID agreementId) {
        Agreements agreement = findOrThrow(agreementId);
        List<AgreementsPurposes> purposes = agreementsPurposesRepo.findByAgreementId(agreementId);
        AgreementMetadata metadata = agreementMetadataRepo.findByAgreementId(agreementId).orElse(null);
        return computeAgreementHash(agreement, purposes, metadata);
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


    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 no disponible en este JVM", e);
        }
    }

    private String normalizeIp(String ipOrigin) {
        if (ipOrigin == null || ipOrigin.isBlank()) {
            return ipOrigin;
        }
        try {
            return InetAddress.getByName(ipOrigin).getHostAddress();
        } catch (UnknownHostException e) {
            return ipOrigin;
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

    /**
     * Calcula el expiresAt de una purpose tomando el período de retención más corto
     * entre todas sus categorías de dato (principio de minimización, Ley 21.719).
     */
    private LocalDateTime calculateExpiresAt(UUID purposeId, LocalDateTime base) {
        return purposeDataCategoryRepo.findByPurposeId(purposeId).stream()
                .map(pdc -> retentionPolicyRepo.findByPurposeDataCategoryId(pdc.getId()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .map(p -> applyRetentionPeriod(base, p.getRetentionPeriod(), p.getRetentionUnit()))
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private LocalDateTime applyRetentionPeriod(LocalDateTime base, Integer period, String unit) {
        return switch (unit.toUpperCase()) {
            case "MONTHS" -> base.plusMonths(period);
            case "YEARS"  -> base.plusYears(period);
            default       -> base.plusDays(period);
        };
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
