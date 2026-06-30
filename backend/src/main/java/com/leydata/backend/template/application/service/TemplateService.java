package com.leydata.backend.template.application.service;

import com.leydata.backend.audit.application.dto.AuditContext;
import com.leydata.backend.audit.application.service.AuditService;
import com.leydata.backend.entity.Purposes;
import com.leydata.backend.entity.TemplatePurposes;
import com.leydata.backend.entity.Templates;
import com.leydata.backend.privacydoc.domain.exception.BusinessValidationException;
import com.leydata.backend.purposes.infrastructure.persistence.PurposesRepository;
import com.leydata.backend.shared.SecurityContextHelper;
import com.leydata.backend.template.application.dto.*;
import com.leydata.backend.template.domain.exception.TemplateNotFoundException;
import com.leydata.backend.template.infrastructure.persistence.TemplatePurposesRepository;
import com.leydata.backend.template.infrastructure.persistence.TemplateSpecifications;
import com.leydata.backend.template.infrastructure.persistence.TemplatesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class TemplateService {

    private final TemplatesRepository templatesRepo;
    private final TemplatePurposesRepository templatePurposesRepo;
    private final PurposesRepository purposesRepo;
    private final SecurityContextHelper securityContextHelper;
    private final AuditService auditService;

    // ── CRUD ─────────────────────────────────────────────────────────────────────

    public TemplateResponse create(CreateTemplateRequest req) {
        securityContextHelper.requireDpoOrAdmin();
        String actorId = securityContextHelper.getKeycloakId();

        String templateKey = req.getTemplateKey().toUpperCase();

        Templates entity = new Templates();
        entity.setTemplateKey(templateKey);
        entity.setVersion(1);
        entity.setName(req.getName());
        entity.setDescription(req.getDescription());
        entity.setTitle(req.getTitle());
        entity.setIsActive(false);
        entity.setCreatedBy(actorId);
        entity.setCreatedAt(OffsetDateTime.now());

        Templates saved = templatesRepo.save(entity);

        auditService.log(AuditContext.builder()
                .tableName("templates")
                .recordId(saved.getId())
                .action("CREAR_TEMPLATE")
                .oldData(null)
                .newData(Map.of(
                        "id",          String.valueOf(saved.getId()),
                        "templateKey", saved.getTemplateKey(),
                        "version",     saved.getVersion()))
                .actorId(actorId)
                .actorRole(securityContextHelper.getActorRole())
                .build());

        return TemplateResponse.from(saved);
    }

    public TemplateResponse newVersion(UUID sourceId) {
        securityContextHelper.requireDpoOrAdmin();
        Templates source = findOrThrow(sourceId);
        String actorId = securityContextHelper.getKeycloakId();

        // Regla 1: mismo TEMPLATE_KEY, versión incremental
        int nextVersion = templatesRepo.findByTemplateKeyOrderByVersionDesc(source.getTemplateKey())
                .stream()
                .mapToInt(Templates::getVersion)
                .max()
                .orElse(source.getVersion()) + 1;

        Templates newTemplate = new Templates();
        newTemplate.setTemplateKey(source.getTemplateKey());
        newTemplate.setVersion(nextVersion);
        newTemplate.setName(source.getName());
        newTemplate.setDescription(source.getDescription());
        newTemplate.setTitle(source.getTitle());
        newTemplate.setIsActive(false);
        newTemplate.setCreatedBy(actorId);
        newTemplate.setCreatedAt(OffsetDateTime.now());

        Templates saved = templatesRepo.save(newTemplate);

        auditService.log(AuditContext.builder()
                .tableName("templates")
                .recordId(saved.getId())
                .action("NUEVA_VERSION_TEMPLATE")
                .oldData(Map.of(
                        "sourceId",      String.valueOf(sourceId),
                        "sourceVersion", String.valueOf(source.getVersion())))
                .newData(Map.of(
                        "id",      String.valueOf(saved.getId()),
                        "version", String.valueOf(saved.getVersion())))
                .actorId(actorId)
                .actorRole(securityContextHelper.getActorRole())
                .build());

        return TemplateResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public TemplateResponse getById(UUID id) {
        securityContextHelper.requireDpoOrAdmin();
        return TemplateResponse.from(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> list(String templateKey, Boolean isActive,
                                        String createdBy, String approvedBy,
                                        OffsetDateTime createdAfter, OffsetDateTime createdBefore) {
        securityContextHelper.requireDpoOrAdmin();
        Specification<Templates> spec = Specification
                .where(TemplateSpecifications.hasTemplateKey(templateKey))
                .and(TemplateSpecifications.isActive(isActive))
                .and(TemplateSpecifications.createdBy(createdBy))
                .and(TemplateSpecifications.approvedBy(approvedBy))
                .and(TemplateSpecifications.createdAfter(createdAfter))
                .and(TemplateSpecifications.createdBefore(createdBefore));

        return templatesRepo.findAll(spec).stream()
                .map(TemplateResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> getHistory(String templateKey) {
        securityContextHelper.requireDpoOrAdmin();
        return templatesRepo.findByTemplateKeyOrderByVersionDesc(templateKey.toUpperCase())
                .stream()
                .map(TemplateResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TemplateResponse getActive(String templateKey) {
        securityContextHelper.requireDpoOrAdmin();
        return templatesRepo.findByTemplateKeyAndIsActiveTrue(templateKey.toUpperCase())
                .map(TemplateResponse::from)
                .orElseThrow(() -> new BusinessValidationException(
                        "No hay una versión activa para el template " + templateKey));
    }

    // ── WORKFLOW ─────────────────────────────────────────────────────────────────

    public TemplateResponse approve(UUID id) {
        securityContextHelper.requireDpoOrAdmin();
        Templates template = findOrThrow(id);

        // Idempotente: si ya está aprobado no se modifica nada
        if (template.getApprovedBy() != null) {
            return TemplateResponse.from(template);
        }
        // Regla 9: debe tener al menos una finalidad visible
        if (!templatePurposesRepo.existsByTemplate_IdAndIsVisibleTrue(id)) {
            throw new BusinessValidationException(
                    "El template debe tener al menos una finalidad visible antes de aprobarse");
        }

        String actorId = securityContextHelper.getKeycloakId();
        template.setApprovedBy(actorId);
        template.setApprovedAt(OffsetDateTime.now());

        Templates saved = templatesRepo.save(template);

        auditService.log(AuditContext.builder()
                .tableName("templates")
                .recordId(saved.getId())
                .action("APROBAR_TEMPLATE")
                .oldData(Map.of("approvedBy", "null"))
                .newData(Map.of("approvedBy", actorId))
                .actorId(actorId)
                .actorRole(securityContextHelper.getActorRole())
                .build());

        return TemplateResponse.from(saved);
    }

    public TemplateResponse activate(UUID id) {
        securityContextHelper.requireDpoOrAdmin();
        Templates template = findOrThrow(id);

        if (Boolean.TRUE.equals(template.getIsActive())) {
            throw new BusinessValidationException("El template ya está activo");
        }
        // Regla 11: no se puede activar una versión obsoleta
        int maxVersion = templatesRepo.findByTemplateKeyOrderByVersionDesc(template.getTemplateKey())
                .stream()
                .mapToInt(Templates::getVersion)
                .max()
                .orElse(template.getVersion());

        if (template.getVersion() < maxVersion) {
            throw new BusinessValidationException(
                    "No se puede activar una versión obsoleta. La versión más reciente es la " + maxVersion);
        }
        // Regla 10: debe estar aprobado y tener al menos un purpose visible
        if (template.getApprovedBy() == null) {
            throw new BusinessValidationException("El template debe estar aprobado antes de activarse");
        }
        if (!templatePurposesRepo.existsByTemplate_IdAndIsVisibleTrue(id)) {
            throw new BusinessValidationException(
                    "El template debe tener al menos una finalidad visible antes de activarse");
        }

        String actorId = securityContextHelper.getKeycloakId();
        String[] previousHash = new String[1];

        // Regla 2: desactivar la versión anterior del mismo TEMPLATE_KEY en la misma transacción
        templatesRepo.findByTemplateKeyAndIsActiveTrue(template.getTemplateKey())
                .ifPresent(previous -> {
                    previousHash[0] = previous.getHashSha256();
                    previous.setIsActive(false);
                    templatesRepo.save(previous);

                    auditService.log(AuditContext.builder()
                            .tableName("templates")
                            .recordId(previous.getId())
                            .action("DESACTIVAR_TEMPLATE_POR_NUEVA_VERSION")
                            .oldData(Map.of("isActive", true))
                            .newData(Map.of("isActive", false))
                            .actorId(actorId)
                            .actorRole(securityContextHelper.getActorRole())
                            .build());
                });

        template.setIsActive(true);
        if (template.getActivationDate() == null) {
            template.setActivationDate(OffsetDateTime.now());
        }

        List<TemplatePurposes> purposes = templatePurposesRepo.findByTemplate_IdOrderByOrderPosition(id);
        template.setHashSha256(computeTemplateHash(template, purposes));
        template.setPreviousHashSha256(previousHash[0]);

        Templates saved = templatesRepo.save(template);

        auditService.log(AuditContext.builder()
                .tableName("templates")
                .recordId(saved.getId())
                .action("ACTIVAR_TEMPLATE")
                .oldData(Map.of("isActive", false))
                .newData(Map.of(
                        "isActive",   true,
                        "hashSha256", saved.getHashSha256()))
                .actorId(actorId)
                .actorRole(securityContextHelper.getActorRole())
                .build());

        return TemplateResponse.from(saved);
    }

    // ── GESTIÓN DE PURPOSES ──────────────────────────────────────────────────────

    public void addPurpose(UUID templateId, AddTemplatePurposeRequest req) {
        securityContextHelper.requireDpoOrAdmin();
        Templates template = findOrThrow(templateId);
        requireDraft(template);

        // Regla 8: ORDER_POSITION único dentro del template
        if (templatePurposesRepo.existsByTemplate_IdAndOrderPosition(templateId, req.getOrderPosition())) {
            throw new BusinessValidationException(
                    "Ya existe una finalidad en la posición " + req.getOrderPosition() + " de este template");
        }

        // Regla 7: solo purposes aprobadas y activas
        Purposes purpose = validatePurposeApproved(req.getPurposeId());

        TemplatePurposes link = new TemplatePurposes();
        link.setId(new TemplatePurposes.TemplatePurposesId(templateId, req.getPurposeId()));
        link.setTemplate(template);
        link.setPurpose(purpose);
        link.setOrderPosition(req.getOrderPosition());
        link.setIsVisible(req.getIsVisible());

        templatePurposesRepo.save(link);

        String actorId = securityContextHelper.getKeycloakId();
        auditService.log(AuditContext.builder()
                .tableName("template_purposes")
                .recordId(templateId)
                .action("VINCULAR_PURPOSE_TEMPLATE")
                .oldData(null)
                .newData(Map.of(
                        "templateId", String.valueOf(templateId),
                        "purposeId",  String.valueOf(req.getPurposeId())))
                .actorId(actorId)
                .actorRole(securityContextHelper.getActorRole())
                .build());
    }

    public void removePurpose(UUID templateId, UUID purposeId) {
        securityContextHelper.requireDpoOrAdmin();
        Templates template = findOrThrow(templateId);
        requireDraft(template);

        templatePurposesRepo.findByTemplate_IdAndPurpose_Id(templateId, purposeId)
                .orElseThrow(() -> new BusinessValidationException(
                        "La finalidad no está vinculada a este template"));

        templatePurposesRepo.deleteByTemplate_IdAndPurpose_Id(templateId, purposeId);

        String actorId = securityContextHelper.getKeycloakId();
        auditService.log(AuditContext.builder()
                .tableName("template_purposes")
                .recordId(templateId)
                .action("DESVINCULAR_PURPOSE_TEMPLATE")
                .oldData(Map.of(
                        "templateId", String.valueOf(templateId),
                        "purposeId",  String.valueOf(purposeId)))
                .newData(null)
                .actorId(actorId)
                .actorRole(securityContextHelper.getActorRole())
                .build());
    }

    @Transactional(readOnly = true)
    public List<TemplatePurposeResponse> listPurposes(UUID templateId) {
        securityContextHelper.requireDpoOrAdmin();
        findOrThrow(templateId);
        return templatePurposesRepo.findByTemplate_IdOrderByOrderPosition(templateId)
                .stream()
                .map(TemplatePurposeResponse::from)
                .toList();
    }

    public TemplatePurposeResponse updatePurpose(UUID templateId, UUID purposeId, UpdateTemplatePurposeRequest req) {
        securityContextHelper.requireDpoOrAdmin();
        Templates template = findOrThrow(templateId);
        requireDraft(template);

        TemplatePurposes link = templatePurposesRepo.findByTemplate_IdAndPurpose_Id(templateId, purposeId)
                .orElseThrow(() -> new BusinessValidationException(
                        "La finalidad no está vinculada a este template"));

        Integer oldOrderPosition = link.getOrderPosition();
        Boolean oldIsVisible = link.getIsVisible();

        if (req.getOrderPosition() != null && !req.getOrderPosition().equals(link.getOrderPosition())) {
            if (templatePurposesRepo.existsByTemplate_IdAndOrderPosition(templateId, req.getOrderPosition())) {
                throw new BusinessValidationException(
                        "Ya existe una finalidad en la posición " + req.getOrderPosition() + " de este template");
            }
            link.setOrderPosition(req.getOrderPosition());
        }

        if (req.getIsVisible() != null) {
            link.setIsVisible(req.getIsVisible());
        }

        TemplatePurposes saved = templatePurposesRepo.save(link);

        String actorId = securityContextHelper.getKeycloakId();
        auditService.log(AuditContext.builder()
                .tableName("template_purposes")
                .recordId(templateId)
                .action("ACTUALIZAR_PURPOSE_TEMPLATE")
                .oldData(Map.of(
                        "orderPosition", String.valueOf(oldOrderPosition),
                        "isVisible",     String.valueOf(oldIsVisible)))
                .newData(Map.of(
                        "orderPosition", String.valueOf(saved.getOrderPosition()),
                        "isVisible",     String.valueOf(saved.getIsVisible())))
                .actorId(actorId)
                .actorRole(securityContextHelper.getActorRole())
                .build());

        return TemplatePurposeResponse.from(saved);
    }

    // ── INTEGRIDAD ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public TemplateVerifyResponse verify(UUID id) {
        securityContextHelper.requireDpoOrAdmin();
        Templates template = findOrThrow(id);

        if (template.getHashSha256() == null) {
            return TemplateVerifyResponse.builder()
                    .templateId(id).version(template.getVersion())
                    .hashMatch(false)
                    .message("El template no tiene hash registrado (nunca fue activado)")
                    .build();
        }

        List<TemplatePurposes> purposes = templatePurposesRepo.findByTemplate_IdOrderByOrderPosition(id);
        String currentHash = computeTemplateHash(template, purposes);
        boolean match = currentHash.equals(template.getHashSha256());

        return TemplateVerifyResponse.builder()
                .templateId(id).version(template.getVersion())
                .hashMatch(match)
                .storedHash(template.getHashSha256())
                .computedHash(currentHash)
                .message(match
                        ? "Integridad verificada: el contenido coincide con el hash registrado"
                        : "ALERTA: el hash no coincide — el template o sus finalidades fueron alterados después de activarse")
                .build();
    }

    // ── INTEGRIDAD (genérica) ──────────────────────────────────────────────────────

    /** Recalcula el hash sin chequeo de permisos — usado por IntegrityVerifier (incl. job SCHEDULED). */
    @Transactional(readOnly = true)
    public String recalculateHash(UUID id) {
        Templates template = findOrThrow(id);
        List<TemplatePurposes> purposes = templatePurposesRepo.findByTemplate_IdOrderByOrderPosition(id);
        return computeTemplateHash(template, purposes);
    }

    // ── VALIDACIONES ─────────────────────────────────────────────────────────────

    private Purposes validatePurposeApproved(UUID purposeId) {
        Purposes purpose = purposesRepo.findById(purposeId)
                .orElseThrow(() -> new BusinessValidationException("La finalidad " + purposeId + " no existe"));
        if (!Boolean.TRUE.equals(purpose.getIsActive()) || purpose.getApprovedBy() == null) {
            throw new BusinessValidationException(
                    "La finalidad " + purposeId + " debe estar aprobada y activa para vincularse a un template");
        }
        return purpose;
    }

    private String computeTemplateHash(Templates template, List<TemplatePurposes> purposes) {
        StringBuilder sb = new StringBuilder()
                .append(template.getTemplateKey()).append("|")
                .append(template.getVersion()).append("|")
                .append(template.getName()).append("|")
                .append(template.getDescription()).append("|")
                .append(template.getTitle());

        for (TemplatePurposes tp : purposes) {
            sb.append("|")
              .append(tp.getPurpose().getId()).append(":")
              .append(tp.getOrderPosition()).append(":")
              .append(tp.getIsVisible());
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 no disponible en este JVM", e);
        }
    }

    private void requireDraft(Templates template) {
        if (Boolean.TRUE.equals(template.getIsActive())) {
            throw new BusinessValidationException(
                    "No se puede modificar un template activo. Cree una nueva versión.");
        }
    }

    private Templates findOrThrow(UUID id) {
        return templatesRepo.findById(id)
                .orElseThrow(() -> new TemplateNotFoundException(id));
    }
}
