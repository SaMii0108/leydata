package com.leydata.backend.purposes.application.service;

import com.leydata.backend.audit.application.dto.AuditContext;
import com.leydata.backend.audit.application.service.AuditService;
import com.leydata.backend.entity.Domains;
import com.leydata.backend.entity.Purposes;
import com.leydata.backend.entity.Users;
import com.leydata.backend.legalbasis.infrastructure.persistence.LegalBasisRepository;
import com.leydata.backend.orgdomain.infrastructure.persistence.DomainsRepository;
import com.leydata.backend.privacydoc.domain.enums.DocumentStatus;
import com.leydata.backend.privacydoc.domain.exception.BusinessValidationException;
import com.leydata.backend.privacydoc.infrastructure.persistence.DocumentPurposesRepository;
import com.leydata.backend.purposes.application.dto.CreatePurposeRequest;
import com.leydata.backend.purposes.application.dto.PurposeResponse;
import com.leydata.backend.purposes.application.dto.UpdatePurposeRequest;
import com.leydata.backend.purposes.domain.exception.PurposeNotFoundException;
import com.leydata.backend.purposes.infrastructure.persistence.PurposesRepository;
import com.leydata.backend.purposedatacategory.infrastructure.persistence.PurposeDataCategoryRepository;
import com.leydata.backend.shared.SecurityContextHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

        Users dpo = securityContextHelper.getAuthenticatedDpo();

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
        purpose.setCreatedBy(dpo.getId());
        purpose.setApprovedBy(dpo.getId());
        purpose.setPurposeRequestId(req.getPurposeRequestId());
        purpose.setCreatedAt(LocalDateTime.now());

        Purposes saved = purposesRepo.save(purpose);

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
                .actorId(dpo.getId())
                .actorRole(securityContextHelper.getActorRole())
                .build());

        return PurposeResponse.from(saved, false);
    }

    // ── CONSULTAS ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PurposeResponse> listAll() {
        String role = securityContextHelper.getActorRole();
        if ("JEFE_DOMINIO".equals(role)) {
            Users user = securityContextHelper.getAuthenticatedUser();
            Set<UUID> domainIds = user.getUserDomains().stream()
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
            Users user = securityContextHelper.getAuthenticatedUser();
            boolean ownsDomain = user.getUserDomains().stream()
                    .anyMatch(ud -> ud.getDomain().getId().equals(purpose.getDomainId()));
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
            Users user = securityContextHelper.getAuthenticatedUser();
            boolean ownsDomain = user.getUserDomains().stream()
                    .anyMatch(ud -> ud.getDomain().getId().equals(domainId));
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

        Purposes saved = purposesRepo.save(purpose);
        Users dpo = securityContextHelper.getAuthenticatedDpo();

        auditService.log(AuditContext.builder()
                .tableName("purposes")
                .recordId(saved.getId())
                .action("PURPOSE_UPDATED")
                .oldData(oldData)
                .newData(Map.of("name", saved.getName(), "required", saved.getRequired()))
                .actorId(dpo.getId())
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
        Users dpo = securityContextHelper.getAuthenticatedDpo();

        auditService.log(AuditContext.builder()
                .tableName("purposes")
                .recordId(saved.getId())
                .action("PURPOSE_DEACTIVATED")
                .oldData(Map.of("isActive", true))
                .newData(Map.of("isActive", false))
                .actorId(dpo.getId())
                .actorRole(securityContextHelper.getActorRole())
                .build());

        return PurposeResponse.from(saved, false);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

    private Purposes findOrThrow(UUID id) {
        return purposesRepo.findById(id)
                .orElseThrow(() -> new PurposeNotFoundException(id));
    }

    private boolean isLocked(UUID purposeId) {
        return documentPurposesRepo
                .existsByPurpose_IdAndDocument_StatusAndIsActiveTrue(purposeId, DocumentStatus.PUBLISHED);
    }

    private void enforceNotLocked(UUID purposeId, String purposeName) {
        if (isLocked(purposeId)) {
            throw new BusinessValidationException(
                    "La finalidad '" + purposeName + "' está publicada en un documento activo y no puede modificarse. " +
                    "Crea una nueva versión del documento para desbloquearla.");
        }
    }
}
