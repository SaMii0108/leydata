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
<<<<<<< HEAD
        securityContextHelper.requireDpoOrAdmin();
        String actorId = securityContextHelper.getKeycloakId();

=======
        UUID actorId = securityContextHelper.getAuthenticatedDpo().getId();

        // Regla 4: el TEMPLATE_KEY siempre se guarda en UPPERCASE
>>>>>>> origin/feature/templates-consentimiento
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
<<<<<<< HEAD
        securityContextHelper.requireDpoOrAdmin();
        Templates source = findOrThrow(sourceId);
        String actorId = securityContextHelper.getKeycloakId();

        // Regla 1: mismo TEMPLATE_KEY, versión incremental
=======
        Templates source = findOrThrow(sourceId);
        UUID actorId = securityContextHelper.getAuthenticatedDpo().getId();

        // Regla 1: mismo TEMPLATE_KEY, versión incremental (VERSION + 1)
>>>>>>> origin/feature/templates-consentimiento
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
<<<<<<< HEAD
        securityContextHelper.requireDpoOrAdmin();
=======
>>>>>>> origin/feature/templates-consentimiento
        return TemplateResponse.from(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> list(String templateKey, Boolean isActive,
<<<<<<< HEAD
                                        String createdBy, String approvedBy,
                                        OffsetDateTime createdAfter, OffsetDateTime createdBefore) {
        securityContextHelper.requireDpoOrAdmin();
=======
                                        UUID createdBy, UUID approvedBy,
                                        OffsetDateTime createdAfter, OffsetDateTime createdBefore) {
>>>>>>> origin/feature/templates-consentimiento
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
<<<<<<< HEAD
        securityContextHelper.requireDpoOrAdmin();
=======
>>>>>>> origin/feature/templates-consentimiento
        return templatesRepo.findByTemplateKeyOrderByVersionDesc(templateKey.toUpperCase())
                .stream()
                .map(TemplateResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TemplateResponse getActive(String templateKey) {
<<<<<<< HEAD
        securityContextHelper.requireDpoOrAdmin();
=======
        // Regla 1: solo una versión activa por TEMPLATE_KEY
>>>>>>> origin/feature/templates-consentimiento
        return templatesRepo.findByTemplateKeyAndIsActiveTrue(templateKey.toUpperCase())
                .map(TemplateResponse::from)
                .orElseThrow(() -> new BusinessValidationException(
                        "No hay una versión activa para el template " + templateKey));
    }

    // ── WORKFLOW ─────────────────────────────────────────────────────────────────

    public TemplateResponse approve(UUID id) {
<<<<<<< HEAD
        securityContextHelper.requireDpoOrAdmin();
        Templates template = findOrThrow(id);

        // Idempotente: si ya está aprobado no se modifica nada
        if (template.getApprovedBy() != null) {
            return TemplateResponse.from(template);
        }
        // Regla 9: debe tener al menos una finalidad visible
=======
        Templates template = findOrThrow(id);

        // Idempotente: si ya está aprobado, no se modifica nada, se devuelve el estado actual
        if (template.getApprovedBy() != null) {
            return TemplateResponse.from(template);
        }
        // Regla 9: el template debe tener al menos una finalidad visible
>>>>>>> origin/feature/templates-consentimiento
        if (!templatePurposesRepo.existsByTemplate_IdAndIsVisibleTrue(id)) {
            throw new BusinessValidationException(
                    "El template debe tener al menos una finalidad visible antes de aprobarse");
        }

<<<<<<< HEAD
        String actorId = securityContextHelper.getKeycloakId();
=======
        UUID actorId = securityContextHelper.getAuthenticatedDpo().getId();
>>>>>>> origin/feature/templates-consentimiento
        template.setApprovedBy(actorId);
        template.setApprovedAt(OffsetDateTime.now());

        Templates saved = templatesRepo.save(template);

        auditService.log(AuditContext.builder()
                .tableName("templates")
                .recordId(saved.getId())
                .action("APROBAR_TEMPLATE")
                .oldData(Map.of("approvedBy", "null"))
<<<<<<< HEAD
                .newData(Map.of("approvedBy", actorId))
=======
                .newData(Map.of("approvedBy", String.valueOf(actorId)))
>>>>>>> origin/feature/templates-consentimiento
                .actorId(actorId)
                .actorRole(securityContextHelper.getActorRole())
                .build());

        return TemplateResponse.from(saved);
    }

    public TemplateResponse activate(UUID id) {
<<<<<<< HEAD
        securityContextHelper.requireDpoOrAdmin();
=======
>>>>>>> origin/feature/templates-consentimiento
        Templates template = findOrThrow(id);

        if (Boolean.TRUE.equals(template.getIsActive())) {
            throw new BusinessValidationException("El template ya está activo");
        }
<<<<<<< HEAD
        // Regla 11: no se puede activar una versión obsoleta
=======
        // Regla 11: no se puede activar una versión obsoleta (debe ser la versión más alta del TEMPLATE_KEY)
>>>>>>> origin/feature/templates-consentimiento
        int maxVersion = templatesRepo.findByTemplateKeyOrderByVersionDesc(template.getTemplateKey())
                .stream()
                .mapToInt(Templates::getVersion)
                .max()
                .orElse(template.getVersion());

        if (template.getVersion() < maxVersion) {
            throw new BusinessValidationException(
                    "No se puede activar una versión obsoleta. La versión más reciente es la " + maxVersion);
        }
<<<<<<< HEAD
        // Regla 10: debe estar aprobado y tener al menos un purpose visible
        if (template.getApprovedBy() == null) {
            throw new BusinessValidationException("El template debe estar aprobado antes de activarse");
        }
=======
        // Regla 10: para activar debe tener APPROVED_BY asignado
        if (template.getApprovedBy() == null) {
            throw new BusinessValidationException("El template debe estar aprobado antes de activarse");
        }
        // Regla 10: para activar debe tener al menos un purpose visible
>>>>>>> origin/feature/templates-consentimiento
        if (!templatePurposesRepo.existsByTemplate_IdAndIsVisibleTrue(id)) {
            throw new BusinessValidationException(
                    "El template debe tener al menos una finalidad visible antes de activarse");
        }

<<<<<<< HEAD
        String actorId = securityContextHelper.getKeycloakId();
=======
        UUID actorId = securityContextHelper.getAuthenticatedDpo().getId();
>>>>>>> origin/feature/templates-consentimiento
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

<<<<<<< HEAD
=======
        // Integridad: hash SHA-256 sobre el contenido sellado del template + sus purposes
>>>>>>> origin/feature/templates-consentimiento
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
<<<<<<< HEAD
        securityContextHelper.requireDpoOrAdmin();
        Templates template = findOrThrow(templateId);
        requireDraft(template);

        // Regla 8: ORDER_POSITION único dentro del template
=======
        Templates template = findOrThrow(templateId);
        requireDraft(template); // Regla 3: no se puede editar un template activo

        // Regla 8: ORDER_POSITION no puede repetirse dentro del mismo template
>>>>>>> origin/feature/templates-consentimiento
        if (templatePurposesRepo.existsByTemplate_IdAndOrderPosition(templateId, req.getOrderPosition())) {
            throw new BusinessValidationException(
                    "Ya existe una finalidad en la posición " + req.getOrderPosition() + " de este template");
        }

<<<<<<< HEAD
        // Regla 7: solo purposes aprobadas y activas
=======
        // Regla 7: solo se vinculan purposes aprobadas y activas
>>>>>>> origin/feature/templates-consentimiento
        Purposes purpose = validatePurposeApproved(req.getPurposeId());

        TemplatePurposes link = new TemplatePurposes();
        link.setId(new TemplatePurposes.TemplatePurposesId(templateId, req.getPurposeId()));
        link.setTemplate(template);
        link.setPurpose(purpose);
        link.setOrderPosition(req.getOrderPosition());
        link.setIsVisible(req.getIsVisible());

        templatePurposesRepo.save(link);

<<<<<<< HEAD
        String actorId = securityContextHelper.getKeycloakId();
=======
>>>>>>> origin/feature/templates-consentimiento
        auditService.log(AuditContext.builder()
                .tableName("template_purposes")
                .recordId(templateId)
                .action("VINCULAR_PURPOSE_TEMPLATE")
                .oldData(null)
                .newData(Map.of(
                        "templateId", String.valueOf(templateId),
                        "purposeId",  String.valueOf(req.getPurposeId())))
<<<<<<< HEAD
                .actorId(actorId)
=======
                .actorId(securityContextHelper.getAuthenticatedDpo().getId())
>>>>>>> origin/feature/templates-consentimiento
                .actorRole(securityContextHelper.getActorRole())
                .build());
    }

    public void removePurpose(UUID templateId, UUID purposeId) {
<<<<<<< HEAD
        securityContextHelper.requireDpoOrAdmin();
        Templates template = findOrThrow(templateId);
        requireDraft(template);
=======
        Templates template = findOrThrow(templateId);
        requireDraft(template); // Regla 3: no se puede editar un template activo
>>>>>>> origin/feature/templates-consentimiento

        templatePurposesRepo.findByTemplate_IdAndPurpose_Id(templateId, purposeId)
                .orElseThrow(() -> new BusinessValidationException(
                        "La finalidad no está vinculada a este template"));

<<<<<<< HEAD
        templatePurposesRepo.deleteByTemplate_IdAndPurpose_Id(templateId, purposeId);

        String actorId = securityContextHelper.getKeycloakId();
=======
        // Hard delete: en DRAFT no hay evidencia que preservar todavía (Regla 6 aplica desde ACTIVE en adelante)
        templatePurposesRepo.deleteByTemplate_IdAndPurpose_Id(templateId, purposeId);

>>>>>>> origin/feature/templates-consentimiento
        auditService.log(AuditContext.builder()
                .tableName("template_purposes")
                .recordId(templateId)
                .action("DESVINCULAR_PURPOSE_TEMPLATE")
                .oldData(Map.of(
                        "templateId", String.valueOf(templateId),
                        "purposeId",  String.valueOf(purposeId)))
                .newData(null)
<<<<<<< HEAD
                .actorId(actorId)
=======
                .actorId(securityContextHelper.getAuthenticatedDpo().getId())
>>>>>>> origin/feature/templates-consentimiento
                .actorRole(securityContextHelper.getActorRole())
                .build());
    }

    @Transactional(readOnly = true)
    public List<TemplatePurposeResponse> listPurposes(UUID templateId) {
<<<<<<< HEAD
        securityContextHelper.requireDpoOrAdmin();
=======
>>>>>>> origin/feature/templates-consentimiento
        findOrThrow(templateId);
        return templatePurposesRepo.findByTemplate_IdOrderByOrderPosition(templateId)
                .stream()
                .map(TemplatePurposeResponse::from)
                .toList();
    }

    public TemplatePurposeResponse updatePurpose(UUID templateId, UUID purposeId, UpdateTemplatePurposeRequest req) {
<<<<<<< HEAD
        securityContextHelper.requireDpoOrAdmin();
        Templates template = findOrThrow(templateId);
        requireDraft(template);
=======
        Templates template = findOrThrow(templateId);
        requireDraft(template); // Regla 3: no se puede editar un template activo
>>>>>>> origin/feature/templates-consentimiento

        TemplatePurposes link = templatePurposesRepo.findByTemplate_IdAndPurpose_Id(templateId, purposeId)
                .orElseThrow(() -> new BusinessValidationException(
                        "La finalidad no está vinculada a este template"));

        Integer oldOrderPosition = link.getOrderPosition();
        Boolean oldIsVisible = link.getIsVisible();

        if (req.getOrderPosition() != null && !req.getOrderPosition().equals(link.getOrderPosition())) {
<<<<<<< HEAD
=======
            // Regla 8: ORDER_POSITION no puede repetirse dentro del mismo template
>>>>>>> origin/feature/templates-consentimiento
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

<<<<<<< HEAD
        String actorId = securityContextHelper.getKeycloakId();
=======
>>>>>>> origin/feature/templates-consentimiento
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
<<<<<<< HEAD
                .actorId(actorId)
=======
                .actorId(securityContextHelper.getAuthenticatedDpo().getId())
>>>>>>> origin/feature/templates-consentimiento
                .actorRole(securityContextHelper.getActorRole())
                .build());

        return TemplatePurposeResponse.from(saved);
    }

<<<<<<< HEAD
    // ── INTEGRIDAD ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public TemplateVerifyResponse verify(UUID id) {
        securityContextHelper.requireDpoOrAdmin();
=======
    // ── UTILIDADES ───────────────────────────────────────────────────────────────

    /**
     * Verifica que el SHA-256 almacenado coincida con el hash recalculado a partir
     * del contenido actual del template y sus purposes. Detecta alteraciones posteriores a la activación.
     */
    @Transactional(readOnly = true)
    public TemplateVerifyResponse verify(UUID id) {
>>>>>>> origin/feature/templates-consentimiento
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

<<<<<<< HEAD
    // ── VALIDACIONES ─────────────────────────────────────────────────────────────
=======
    // ── Validaciones de negocio ───────────────────────────────────────────────────
>>>>>>> origin/feature/templates-consentimiento

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
<<<<<<< HEAD
=======
        // Regla 3: no se puede editar un template activo, se debe crear nueva versión
>>>>>>> origin/feature/templates-consentimiento
        if (Boolean.TRUE.equals(template.getIsActive())) {
            throw new BusinessValidationException(
                    "No se puede modificar un template activo. Cree una nueva versión.");
        }
    }

<<<<<<< HEAD
=======
    // ── Helpers ──────────────────────────────────────────────────────────────────

>>>>>>> origin/feature/templates-consentimiento
    private Templates findOrThrow(UUID id) {
        return templatesRepo.findById(id)
                .orElseThrow(() -> new TemplateNotFoundException(id));
    }
}
