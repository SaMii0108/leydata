package com.leydata.backend.purposes.application.service;

import com.leydata.backend.agreement.infrastructure.persistence.AgreementsRepository;
import com.leydata.backend.audit.application.dto.AuditContext;
import com.leydata.backend.audit.application.service.AuditService;
import com.leydata.backend.entity.Domains;
import com.leydata.backend.entity.Purposes;
import com.leydata.backend.legalbasis.infrastructure.persistence.LegalBasisRepository;
import com.leydata.backend.orgdomain.infrastructure.persistence.DomainsRepository;
import com.leydata.backend.privacydoc.domain.enums.DocumentStatus;
import com.leydata.backend.privacydoc.domain.exception.BusinessValidationException;
import com.leydata.backend.privacydoc.infrastructure.persistence.DocumentPurposesRepository;
import com.leydata.backend.purposes.application.dto.CreatePurposeRequest;
import com.leydata.backend.purposes.application.dto.PurposeResponse;
import com.leydata.backend.purposes.application.dto.UpdatePurposeRequest;
import com.leydata.backend.agreement.infrastructure.persistence.AgreementsRepository;
import com.leydata.backend.purposes.domain.exception.PurposeNotFoundException;
import com.leydata.backend.purposes.domain.exception.PurposeNotLockedException;
import com.leydata.backend.purposes.infrastructure.persistence.PurposesRepository;
import com.leydata.backend.purposedatacategory.infrastructure.persistence.PurposeDataCategoryRepository;
import com.leydata.backend.shared.SecurityContextHelper;
import com.leydata.backend.template.infrastructure.persistence.TemplatePurposesRepository;
import com.leydata.backend.userdomain.infrastructure.persistence.UserDomainRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class PurposeService {

    private final PurposesRepository purposesRepo;
    private final LegalBasisRepository legalBasisRepo;
    private final DomainsRepository domainsRepo;
    private final DocumentPurposesRepository documentPurposesRepo;
    private final PurposeDataCategoryRepository pdcRepo;
    private final AuditService auditService;
    private final SecurityContextHelper securityContextHelper;
    private final UserDomainRepository userDomainRepository;
    private final TemplatePurposesRepository templatePurposesRepo;
    private final AgreementsRepository agreementsRepo;

    // ── CREAR ─────────────────────────────────────────────────────────────────────

    public PurposeResponse create(CreatePurposeRequest req) {
        String code = req.getCode().toUpperCase();

        if (purposesRepo.existsByCode(code)) {
            throw new BusinessValidationException(
                    "Ya existe una finalidad con código: " + code);
        }

        legalBasisRepo.findById(req.getLegalBasisId())
                .orElseThrow(() -> new BusinessValidationException(
                        "Base de licitud no encontrada: " + req.getLegalBasisId()));

        Domains domain = domainsRepo.findById(req.getDomainId())
                .orElseThrow(() -> new BusinessValidationException(
                        "Dominio no encontrado: " + req.getDomainId()));
        if (!Boolean.TRUE.equals(domain.getActive())) {
            throw new BusinessValidationException(
                    "El dominio está desactivado: " + domain.getName());
        }

        String actorId = securityContextHelper.getKeycloakId();
        String actorName = securityContextHelper.getName();

        Purposes purpose = new Purposes();
        purpose.setCode(code);
        purpose.setName(req.getName());
        purpose.setDescription(req.getDescription());
        purpose.setShortDescription(req.getShortDescription());
        purpose.setRequired(req.getRequired());
        purpose.setRevocable(req.getRevocable());
        purpose.setPresentationOrder(req.getPresentationOrder());
        purpose.setLegalBasisId(req.getLegalBasisId());
        purpose.setDomainId(req.getDomainId());
        purpose.setConsentStatement(req.getConsentStatement());
        purpose.setIsActive(true);
        purpose.setCreatedBy(actorId);
        purpose.setCreatedByName(actorName);
        purpose.setApprovedBy(actorId);
        purpose.setApprovedByName(actorName);
        purpose.setPurposeRequestId(req.getPurposeRequestId());
        purpose.setCreatedAt(LocalDateTime.now());
        purpose.setVersion(1);
        purpose.setStatus("ACTIVE");
        purpose.setHashSha256(computeHash(purpose));

        Purposes saved = purposesRepo.save(purpose);

        // purpose_family_id = id propio: self-reference que agrupa futuras versiones de esta finalidad
        saved.setPurposeFamilyId(saved.getId());
        saved = purposesRepo.save(saved);

        auditService.log(AuditContext.builder()
                .tableName("purposes")
                .recordId(saved.getId())
                .action("PURPOSE_CREATED")
                .oldData(null)
                .newData(Map.of(
                        "code", saved.getCode(),
                        "name", saved.getName(),
                        "required", saved.getRequired(),
                        "legalBasisId", saved.getLegalBasisId().toString()))
                .actorId(actorId)
                .actorRole(securityContextHelper.getActorRole())
                .build());

        return PurposeResponse.from(saved, false);
    }

    // ── CONSULTAS ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PurposeResponse> listAll() {
        String role = securityContextHelper.getActorRole();
        if ("JEFE_DOMINIO".equals(role)) {
            Set<UUID> domainIds = userDomainRepository.findByKeycloakId(securityContextHelper.getKeycloakId())
                    .stream()
                    .map(ud -> ud.getDomain().getId())
                    .collect(Collectors.toSet());
            return domainIds.stream()
                    .flatMap(dId -> purposesRepo.findByDomainIdAndIsActiveTrue(dId).stream())
                    .map(p -> PurposeResponse.from(p, isLocked(p.getId())))
                    .toList();
        }
        return purposesRepo.findByIsActiveTrue().stream()
                .map(p -> PurposeResponse.from(p, isLocked(p.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PurposeResponse getById(UUID id) {
        Purposes purpose = findOrThrow(id);
        String role = securityContextHelper.getActorRole();
        if ("JEFE_DOMINIO".equals(role)) {
            boolean ownsDomain = userDomainRepository.existsByKeycloakIdAndDomainId(
                    securityContextHelper.getKeycloakId(), purpose.getDomainId());
            if (!ownsDomain) {
                throw new AccessDeniedException("No tienes acceso a esta finalidad");
            }
        }
        return PurposeResponse.from(purpose, isLocked(id));
    }

    @Transactional(readOnly = true)
    public List<PurposeResponse> listByDomain(UUID domainId) {
        String role = securityContextHelper.getActorRole();
        if ("JEFE_DOMINIO".equals(role)) {
            boolean ownsDomain = userDomainRepository.existsByKeycloakIdAndDomainId(
                    securityContextHelper.getKeycloakId(), domainId);
            if (!ownsDomain) {
                throw new AccessDeniedException("No tienes acceso a las finalidades de este dominio");
            }
        }
        return purposesRepo.findByDomainIdAndIsActiveTrue(domainId).stream()
                .map(p -> PurposeResponse.from(p, isLocked(p.getId())))
                .toList();
    }

    // ── EDITAR ────────────────────────────────────────────────────────────────────

    public PurposeResponse update(UUID id, UpdatePurposeRequest req) {
        Purposes purpose = findOrThrow(id);
        enforceNotLocked(id, purpose.getName());

        Map<String, Object> oldData = Map.of(
                "name", purpose.getName(),
                "required", purpose.getRequired());

        if (req.getName() != null)              purpose.setName(req.getName());
        if (req.getDescription() != null)       purpose.setDescription(req.getDescription());
        if (req.getShortDescription() != null)  purpose.setShortDescription(req.getShortDescription());
        if (req.getConsentStatement() != null)  purpose.setConsentStatement(req.getConsentStatement());
        if (req.getRequired() != null)          purpose.setRequired(req.getRequired());
        if (req.getRevocable() != null)         purpose.setRevocable(req.getRevocable());
        if (req.getPresentationOrder() != null) purpose.setPresentationOrder(req.getPresentationOrder());
        if (req.getLegalBasisId() != null) {
            legalBasisRepo.findById(req.getLegalBasisId())
                    .orElseThrow(() -> new BusinessValidationException(
                            "Base de licitud no encontrada: " + req.getLegalBasisId()));
            purpose.setLegalBasisId(req.getLegalBasisId());
        }
        purpose.setUpdatedAt(LocalDateTime.now());
        purpose.setHashSha256(computeHash(purpose));

        Purposes saved = purposesRepo.save(purpose);

        auditService.log(AuditContext.builder()
                .tableName("purposes")
                .recordId(saved.getId())
                .action("PURPOSE_UPDATED")
                .oldData(oldData)
                .newData(Map.of("name", saved.getName(), "required", saved.getRequired()))
                .actorId(securityContextHelper.getKeycloakId())
                .actorRole(securityContextHelper.getActorRole())
                .build());

        return PurposeResponse.from(saved, false);
    }

    // ── DESACTIVAR ────────────────────────────────────────────────────────────────

    public PurposeResponse deactivate(UUID id) {
        Purposes purpose = findOrThrow(id);
        enforceNotLocked(id, purpose.getName());

        if (pdcRepo.existsByPurposeId(id)) {
            throw new BusinessValidationException(
                    "La finalidad '" + purpose.getName() + "' tiene categorías de datos activas. " +
                    "Desvincula primero todas las categorías antes de desactivar la finalidad.");
        }

        purpose.setIsActive(false);
        purpose.setUpdatedAt(LocalDateTime.now());
        Purposes saved = purposesRepo.save(purpose);

        auditService.log(AuditContext.builder()
                .tableName("purposes")
                .recordId(saved.getId())
                .action("PURPOSE_DEACTIVATED")
                .oldData(Map.of("isActive", true))
                .newData(Map.of("isActive", false))
                .actorId(securityContextHelper.getKeycloakId())
                .actorRole(securityContextHelper.getActorRole())
                .build());

        return PurposeResponse.from(saved, false);
    }

    // ── VERSIONADO ───────────────────────────────────────────────────────────────

    public PurposeResponse newVersion(UUID sourceId, UpdatePurposeRequest req) {
        Purposes source = findOrThrow(sourceId);

        if (!isLocked(sourceId)) {
            throw new PurposeNotLockedException(source.getName());
        }

        UUID familyId = source.getPurposeFamilyId() != null ? source.getPurposeFamilyId() : source.getId();
        int nextVersion = purposesRepo.findByPurposeFamilyIdOrderByVersionDesc(familyId).stream()
                .mapToInt(Purposes::getVersion)
                .max()
                .orElse(source.getVersion()) + 1;

        String actorId = securityContextHelper.getKeycloakId();
        String actorName = securityContextHelper.getName();

        Purposes newPurpose = new Purposes();
        newPurpose.setPurposeFamilyId(familyId);
        newPurpose.setVersion(nextVersion);
        newPurpose.setStatus("ACTIVE");
        newPurpose.setCode(source.getCode());
        newPurpose.setName(req.getName() != null ? req.getName() : source.getName());
        newPurpose.setDescription(req.getDescription() != null ? req.getDescription() : source.getDescription());
        newPurpose.setShortDescription(req.getShortDescription() != null ? req.getShortDescription() : source.getShortDescription());
        newPurpose.setConsentStatement(req.getConsentStatement() != null ? req.getConsentStatement() : source.getConsentStatement());
        newPurpose.setRequired(req.getRequired() != null ? req.getRequired() : source.getRequired());
        newPurpose.setRevocable(req.getRevocable() != null ? req.getRevocable() : source.getRevocable());
        newPurpose.setPresentationOrder(req.getPresentationOrder() != null ? req.getPresentationOrder() : source.getPresentationOrder());
        newPurpose.setLegalBasisId(req.getLegalBasisId() != null ? req.getLegalBasisId() : source.getLegalBasisId());
        newPurpose.setDomainId(source.getDomainId());
        newPurpose.setIsActive(true);
        newPurpose.setCreatedBy(actorId);
        newPurpose.setCreatedByName(actorName);
        newPurpose.setApprovedBy(actorId);
        newPurpose.setApprovedByName(actorName);
        newPurpose.setCreatedAt(LocalDateTime.now());
        newPurpose.setHashSha256(computeHash(newPurpose));

        Purposes saved = purposesRepo.save(newPurpose);

        source.setStatus("SUPERSEDED");
        purposesRepo.save(source);

        auditService.log(AuditContext.builder()
                .tableName("purposes")
                .recordId(saved.getId())
                .action("PURPOSE_NEW_VERSION_CREATED")
                .oldData(Map.of(
                        "sourceId", String.valueOf(sourceId),
                        "sourceVersion", String.valueOf(source.getVersion())))
                .newData(Map.of(
                        "id", String.valueOf(saved.getId()),
                        "version", String.valueOf(saved.getVersion())))
                .actorId(actorId)
                .actorRole(securityContextHelper.getActorRole())
                .build());

        return PurposeResponse.from(saved, isLocked(saved.getId()));
    }

    @Transactional(readOnly = true)
    public List<PurposeResponse> getFamily(UUID purposeFamilyId) {
        return purposesRepo.findByPurposeFamilyIdOrderByVersionDesc(purposeFamilyId).stream()
                .map(p -> PurposeResponse.from(p, isLocked(p.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PurposeResponse getActiveByFamily(UUID purposeFamilyId) {
        return purposesRepo.findByPurposeFamilyIdAndStatus(purposeFamilyId, "ACTIVE")
                .map(p -> PurposeResponse.from(p, isLocked(p.getId())))
                .orElseThrow(() -> new BusinessValidationException(
                        "No hay una versión ACTIVE para la familia " + purposeFamilyId));
    }

    // ── INTEGRIDAD ───────────────────────────────────────────────────────────────

    /** Recalcula el hash sobre los campos vigentes en BD — usado por IntegrityVerifier. */
    @Transactional(readOnly = true)
    public String recalculateHash(UUID purposeId) {
        return computeHash(findOrThrow(purposeId));
    }

    private String computeHash(Purposes p) {
        String content = String.join("|",
                String.valueOf(p.getCode()),
                String.valueOf(p.getName()),
                String.valueOf(p.getDescription()),
                String.valueOf(p.getShortDescription()),
                String.valueOf(p.getRequired()),
                String.valueOf(p.getRevocable()),
                String.valueOf(p.getConsentStatement()),
                String.valueOf(p.getLegalBasisId()),
                String.valueOf(p.getDomainId()));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 no disponible en este JVM", e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

    private Purposes findOrThrow(UUID id) {
        return purposesRepo.findById(id)
                .orElseThrow(() -> new PurposeNotFoundException(id));
    }

    private boolean isLocked(UUID purposeId) {
        return isLockedByDocument(purposeId) || isLockedByAgreement(purposeId);
    }

    private boolean isLockedByDocument(UUID purposeId) {
        return documentPurposesRepo
                .existsByPurpose_IdAndDocument_StatusAndIsActiveTrue(purposeId, DocumentStatus.PUBLISHED);
    }

    private boolean isLockedByAgreement(UUID purposeId) {
        List<UUID> templateIds = templatePurposesRepo.findByPurpose_Id(purposeId).stream()
                .map(tp -> tp.getTemplate().getId())
                .toList();
        return !templateIds.isEmpty() && agreementsRepo.existsByTemplateIdIn(templateIds);
    }

    private void enforceNotLocked(UUID purposeId, String purposeName) {
        if (isLocked(purposeId)) {
            throw new BusinessValidationException(
                    "La finalidad '" + purposeName + "' está publicada en un documento activo y no puede modificarse. " +
                    "Crea una nueva versión del documento para desbloquearla.");
        }
    }
}
