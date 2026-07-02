package com.leydata.backend.privacydoc.application.service;

import com.leydata.backend.audit.application.dto.AuditContext;
import com.leydata.backend.audit.application.service.AuditService;
import com.leydata.backend.entity.DocumentPurposes;
import com.leydata.backend.entity.DocumentTemplates;
import com.leydata.backend.entity.PrivacyDocuments;
import com.leydata.backend.entity.Templates;
import com.leydata.backend.notification.application.service.NotificationService;
import com.leydata.backend.notification.domain.enums.NotificationType;
import com.leydata.backend.orgdomain.infrastructure.persistence.DomainsRepository;
import com.leydata.backend.privacydoc.application.dto.*;
import com.leydata.backend.privacydoc.domain.enums.DocumentCategory;
import com.leydata.backend.privacydoc.domain.enums.DocumentStatus;
import com.leydata.backend.privacydoc.domain.exception.BusinessValidationException;
import com.leydata.backend.privacydoc.domain.exception.DocumentNotFoundException;
import com.leydata.backend.privacydoc.domain.exception.InvalidTransitionException;
import com.leydata.backend.purposes.domain.exception.PurposeNotFoundException;
import com.leydata.backend.privacydoc.infrastructure.pdf.PdfGeneratorService;
import com.leydata.backend.privacydoc.infrastructure.persistence.DocumentPurposesRepository;
import com.leydata.backend.privacydoc.infrastructure.persistence.DocumentTemplatesRepository;
import com.leydata.backend.privacydoc.infrastructure.persistence.PrivacyDocumentsRepository;
import com.leydata.backend.purposerequest.infrastructure.persistence.PurposeRequestsRepository;
import com.leydata.backend.purposes.infrastructure.persistence.PurposesRepository;
import com.leydata.backend.template.infrastructure.persistence.TemplatesRepository;
import com.leydata.backend.shared.EmailService;
import com.leydata.backend.shared.SecurityContextHelper;
import com.leydata.backend.userdomain.infrastructure.persistence.UserDomainRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PrivacyDocumentService {

    private final PrivacyDocumentsRepository documentRepo;
    private final DocumentPurposesRepository purposeRepo;
    private final DocumentTemplatesRepository templateLinkRepo;
    private final DomainsRepository domainsRepo;
    private final PurposesRepository purposesRepo;
    private final PurposeRequestsRepository purposeRequestsRepo;
    private final TemplatesRepository templatesRepo;
    private final PdfGeneratorService pdfGenerator;
    private final NotificationService notificationService;
    private final Optional<EmailService> emailService;
    private final UserDomainRepository userDomainRepository;
    private final AuditService auditService;
    private final SecurityContextHelper securityContextHelper;

    // ── CRUD DRAFT ───────────────────────────────────────────────────────────────

    public PrivacyDocumentResponse create(CreateDocumentRequest req) {
        String actorId = securityContextHelper.getKeycloakId();
        String actorName = securityContextHelper.getName();

        PrivacyDocuments entity = PrivacyDocuments.builder()
                .category(req.getCategory())
                .status(DocumentStatus.DRAFT)
                .version(1)
                .name(req.getName())
                .content(req.getContent())
                .isActive(true)
                .createdBy(actorId)
                .createdByName(actorName)
                .build();

        PrivacyDocuments saved = documentRepo.save(entity);

        // family_id = id propio: self-reference que agrupa futuras versiones de este documento
        saved.setDocumentFamilyId(saved.getId());
        saved = documentRepo.save(saved);

        auditService.log(AuditContext.builder()
                .tableName("privacy_documents")
                .recordId(saved.getId())
                .action("DOCUMENT_CREATED")
                .oldData(null)
                .newData(Map.of(
                        "id",       String.valueOf(saved.getId()),
                        "name",     saved.getName(),
                        "category", String.valueOf(saved.getCategory()),
                        "status",   String.valueOf(saved.getStatus())))
                .actorId(actorId)
                .actorRole(securityContextHelper.getActorRole())
                .build());

        return PrivacyDocumentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PrivacyDocumentResponse getById(UUID id) {
        PrivacyDocuments doc = findOrThrow(id);
        if (!isPrivilegedUser()) {
            if (doc.getStatus() != DocumentStatus.PUBLISHED) {
                throw new AccessDeniedException("No tienes permiso para ver este documento");
            }
            return PrivacyDocumentResponse.fromPublic(doc);
        }
        return PrivacyDocumentResponse.from(doc);
    }

    @Transactional(readOnly = true)
    public List<PrivacyDocumentResponse> list(DocumentCategory category, DocumentStatus status) {
        boolean privileged = isPrivilegedUser();
        DocumentStatus effectiveStatus = privileged ? status : DocumentStatus.PUBLISHED;
        return documentRepo.findByFilters(category, effectiveStatus)
                .stream()
                .map(privileged ? PrivacyDocumentResponse::from : PrivacyDocumentResponse::fromPublic)
                .toList();
    }

    public PrivacyDocumentResponse update(UUID id, UpdateDocumentRequest req) {
        PrivacyDocuments doc = findOrThrow(id);
        requireStatus(doc, DocumentStatus.DRAFT);

        Map<String, Object> oldData = Map.of(
                "name",    doc.getName() != null ? doc.getName() : "",
                "version", doc.getVersion());

        if (req.getName() != null)       doc.setName(req.getName());
        if (req.getContent() != null)    doc.setContent(req.getContent());

        PrivacyDocuments saved = documentRepo.save(doc);

        auditService.log(AuditContext.builder()
                .tableName("privacy_documents")
                .recordId(saved.getId())
                .action("DOCUMENT_UPDATED")
                .oldData(oldData)
                .newData(Map.of(
                        "name",    saved.getName() != null ? saved.getName() : "",
                        "version", saved.getVersion()))
                .actorId(securityContextHelper.getKeycloakId())
                .actorRole(securityContextHelper.getActorRole())
                .build());

        return PrivacyDocumentResponse.from(saved);
    }

    public PrivacyDocumentResponse deactivate(UUID id) {
        PrivacyDocuments doc = findOrThrow(id);

        // Un documento solo puede desactivarse si no tiene finalidades activas asociadas
        if (purposeRepo.existsByDocument_IdAndIsActiveTrue(id)) {
            throw new BusinessValidationException(
                    "No se puede desactivar el documento: tiene finalidades activas asociadas. " +
                    "Desvinculá todas las finalidades primero.");
        }

        doc.setIsActive(false);
        PrivacyDocuments saved = documentRepo.save(doc);

        auditService.log(AuditContext.builder()
                .tableName("privacy_documents")
                .recordId(saved.getId())
                .action("DOCUMENT_DEACTIVATED")
                .oldData(Map.of("isActive", true,  "status", String.valueOf(doc.getStatus())))
                .newData(Map.of("isActive", false, "status", String.valueOf(saved.getStatus())))
                .actorId(securityContextHelper.getKeycloakId())
                .actorRole(securityContextHelper.getActorRole())
                .build());

        return PrivacyDocumentResponse.from(saved);
    }

    // ── GESTIÓN DE PROPÓSITOS ────────────────────────────────────────────────────

    public void addPurpose(UUID documentId, UUID purposeId) {
        PrivacyDocuments doc = findOrThrow(documentId);
        requireStatus(doc, DocumentStatus.DRAFT);

        if (purposeRepo.existsByDocument_IdAndPurpose_IdAndIsActiveTrue(documentId, purposeId)) {
            throw new BusinessValidationException("La finalidad ya está activamente vinculada a este documento");
        }

        validatePurposeApproved(purposeId);

        var purposeEntity = purposesRepo.findById(purposeId)
                .orElseThrow(() -> new PurposeNotFoundException(purposeId));

        // Si existía un vínculo inactivo (soft-deleted), lo reactivamos en lugar de insertar
        DocumentPurposes link = purposeRepo.findByDocument_IdAndPurpose_Id(documentId, purposeId)
                .orElseGet(() -> {
                    DocumentPurposes newLink = new DocumentPurposes();
                    newLink.setId(new DocumentPurposes.DocumentPurposesId(documentId, purposeId));
                    newLink.setDocument(doc);
                    newLink.setPurpose(purposeEntity);
                    return newLink;
                });
        link.setIsActive(true);
        purposeRepo.save(link);

        auditService.log(AuditContext.builder()
                .tableName("privacy_documents")
                .recordId(documentId)
                .action("DOCUMENT_PURPOSE_ADDED")
                .oldData(null)
                .newData(Map.of(
                        "documentId", String.valueOf(documentId),
                        "purposeId",  String.valueOf(purposeId)))
                .actorId(securityContextHelper.getKeycloakId())
                .actorRole(securityContextHelper.getActorRole())
                .build());
    }

    public void removePurpose(UUID documentId, UUID purposeId) {
        PrivacyDocuments doc = findOrThrow(documentId);
        requireStatus(doc, DocumentStatus.DRAFT);

        DocumentPurposes link = purposeRepo.findByDocument_IdAndPurpose_Id(documentId, purposeId)
                .filter(l -> Boolean.TRUE.equals(l.getIsActive()))
                .orElseThrow(() -> new BusinessValidationException(
                        "La finalidad no está activamente vinculada a este documento"));

        // Soft delete: el vínculo queda en BD como registro histórico
        link.setIsActive(false);
        purposeRepo.save(link);

        auditService.log(AuditContext.builder()
                .tableName("privacy_documents")
                .recordId(documentId)
                .action("DOCUMENT_PURPOSE_REMOVED")
                .oldData(Map.of(
                        "documentId", String.valueOf(documentId),
                        "purposeId",  String.valueOf(purposeId)))
                .newData(null)
                .actorId(securityContextHelper.getKeycloakId())
                .actorRole(securityContextHelper.getActorRole())
                .build());
    }

    // ── GESTIÓN DE TEMPLATES ─────────────────────────────────────────────────────
    // El documento es el dueño del vínculo: se puede asociar/desasociar en cualquier
    // estado y no se exige que el template esté ACTIVE. El documento se publica de
    // forma independiente — no requiere ningún template asociado.

    public void addTemplate(UUID documentId, UUID templateId) {
        PrivacyDocuments doc = findOrThrow(documentId);

        if (templateLinkRepo.existsByDocument_IdAndTemplate_IdAndIsActiveTrue(documentId, templateId)) {
            throw new BusinessValidationException("El template ya está activamente vinculado a este documento");
        }

        Templates templateEntity = templatesRepo.findById(templateId)
                .orElseThrow(() -> new java.util.NoSuchElementException("La template " + templateId + " no existe"));

        // Si existía un vínculo inactivo (soft-deleted), lo reactivamos en lugar de insertar
        DocumentTemplates link = templateLinkRepo.findByDocument_IdAndTemplate_Id(documentId, templateId)
                .orElseGet(() -> {
                    DocumentTemplates newLink = new DocumentTemplates();
                    newLink.setId(new DocumentTemplates.DocumentTemplatesId(documentId, templateId));
                    newLink.setDocument(doc);
                    newLink.setTemplate(templateEntity);
                    return newLink;
                });
        link.setIsActive(true);
        templateLinkRepo.save(link);

        auditService.log(AuditContext.builder()
                .tableName("privacy_documents")
                .recordId(documentId)
                .action("DOCUMENT_TEMPLATE_ADDED")
                .oldData(null)
                .newData(Map.of(
                        "documentId", String.valueOf(documentId),
                        "templateId", String.valueOf(templateId)))
                .actorId(securityContextHelper.getKeycloakId())
                .actorRole(securityContextHelper.getActorRole())
                .build());
    }

    public void removeTemplate(UUID documentId, UUID templateId) {
        findOrThrow(documentId);

        DocumentTemplates link = templateLinkRepo.findByDocument_IdAndTemplate_Id(documentId, templateId)
                .filter(l -> Boolean.TRUE.equals(l.getIsActive()))
                .orElseThrow(() -> new BusinessValidationException(
                        "El template no está activamente vinculado a este documento"));

        // Soft delete: el vínculo queda en BD como registro histórico
        link.setIsActive(false);
        templateLinkRepo.save(link);

        auditService.log(AuditContext.builder()
                .tableName("privacy_documents")
                .recordId(documentId)
                .action("DOCUMENT_TEMPLATE_REMOVED")
                .oldData(Map.of(
                        "documentId", String.valueOf(documentId),
                        "templateId", String.valueOf(templateId)))
                .newData(null)
                .actorId(securityContextHelper.getKeycloakId())
                .actorRole(securityContextHelper.getActorRole())
                .build());
    }

    // ── WORKFLOW DE ESTADOS ──────────────────────────────────────────────────────

    public PrivacyDocumentResponse submit(UUID id) {
        PrivacyDocuments doc = findOrThrow(id);
        requireStatus(doc, DocumentStatus.DRAFT);
        validateTransition(doc.getStatus(), DocumentStatus.IN_REVIEW);
        validateReadyForReview(doc);

        String previousStatus = String.valueOf(doc.getStatus());
        doc.setStatus(DocumentStatus.IN_REVIEW);
        PrivacyDocuments saved = documentRepo.save(doc);

        auditService.log(AuditContext.builder()
                .tableName("privacy_documents")
                .recordId(saved.getId())
                .action("DOCUMENT_SUBMITTED")
                .oldData(Map.of("status", previousStatus))
                .newData(Map.of("status", String.valueOf(saved.getStatus())))
                .actorId(securityContextHelper.getKeycloakId())
                .actorRole(securityContextHelper.getActorRole())
                .build());

        return PrivacyDocumentResponse.from(saved);
    }

    public PrivacyDocumentResponse resubmit(UUID id) {
        PrivacyDocuments doc = findOrThrow(id);
        requireStatus(doc, DocumentStatus.REJECTED);
        validateTransition(doc.getStatus(), DocumentStatus.IN_REVIEW);
        validateReadyForReview(doc);

        doc.setStatus(DocumentStatus.IN_REVIEW);
        doc.setRejectionReason(null);
        PrivacyDocuments saved = documentRepo.save(doc);

        auditService.log(AuditContext.builder()
                .tableName("privacy_documents")
                .recordId(saved.getId())
                .action("DOCUMENT_RESUBMITTED")
                .oldData(Map.of("status", "REJECTED"))
                .newData(Map.of("status", String.valueOf(saved.getStatus())))
                .actorId(securityContextHelper.getKeycloakId())
                .actorRole(securityContextHelper.getActorRole())
                .build());

        return PrivacyDocumentResponse.from(saved);
    }

    public PrivacyDocumentResponse approve(UUID id) {
        String actorId = securityContextHelper.getKeycloakId();
        String actorName = securityContextHelper.getName();
        PrivacyDocuments doc = findOrThrow(id);
        validateTransition(doc.getStatus(), DocumentStatus.APPROVED);

        doc.setStatus(DocumentStatus.APPROVED);
        doc.setApprovedBy(actorId);
        doc.setApprovedByName(actorName);
        doc.setRejectionReason(null);
        PrivacyDocuments saved = documentRepo.save(doc);

        auditService.log(AuditContext.builder()
                .tableName("privacy_documents")
                .recordId(saved.getId())
                .action("DOCUMENT_APPROVED")
                .oldData(Map.of("status", "IN_REVIEW"))
                .newData(Map.of(
                        "status",     String.valueOf(saved.getStatus()),
                        "approvedBy", String.valueOf(actorId)))
                .actorId(actorId)
                .actorRole(securityContextHelper.getActorRole())
                .build());

        return PrivacyDocumentResponse.from(saved);
    }

    public PrivacyDocumentResponse reject(UUID id, RejectDocumentRequest req) {
        PrivacyDocuments doc = findOrThrow(id);
        validateTransition(doc.getStatus(), DocumentStatus.REJECTED);

        doc.setStatus(DocumentStatus.REJECTED);
        doc.setRejectionReason(req.getReason());
        PrivacyDocuments saved = documentRepo.save(doc);

        auditService.log(AuditContext.builder()
                .tableName("privacy_documents")
                .recordId(saved.getId())
                .action("DOCUMENT_REJECTED")
                .oldData(Map.of("status", "IN_REVIEW"))
                .newData(Map.of(
                        "status",          String.valueOf(saved.getStatus()),
                        "rejectionReason", req.getReason() != null ? req.getReason() : ""))
                .actorId(securityContextHelper.getKeycloakId())
                .actorRole(securityContextHelper.getActorRole())
                .build());

        return PrivacyDocumentResponse.from(saved);
    }

    /**
     * APPROVED → PUBLISHED.
     * Evento atómico: genera PDF + SHA-256, archiva versión anterior, sella el documento.
     */
    public PrivacyDocumentResponse publish(UUID id) {
        String publishedBy = securityContextHelper.getKeycloakId();
        PrivacyDocuments doc = findOrThrow(id);
        validateTransition(doc.getStatus(), DocumentStatus.PUBLISHED);

        List<DocumentPurposes> activePurposes = purposeRepo.findByDocument_IdAndIsActiveTrue(id);
        if (activePurposes.isEmpty()) {
            throw new BusinessValidationException(
                "El documento debe tener al menos una finalidad activa para publicarse.");
        }
        boolean allApproved = activePurposes.stream()
            .allMatch(dp -> dp.getPurpose().getApprovedBy() != null && Boolean.TRUE.equals(dp.getPurpose().getIsActive()));
        if (!allApproved) {
            throw new BusinessValidationException(
                "Todas las finalidades vinculadas deben estar en estado APPROVED para publicar el documento.");
        }

        // Regla: a lo sumo un documento PUBLISHED por template. Por cada template vinculado,
        // si ya existe otro documento PUBLISHED que lo comparte, se archiva automáticamente
        // — mismo patrón que TemplateService.activate() con isActive.
        List<UUID> linkedTemplateIds = templateLinkRepo.findByDocument_IdAndIsActiveTrue(id).stream()
                .map(dt -> dt.getId().getTemplateId())
                .toList();

        linkedTemplateIds.stream()
                .flatMap(templateId -> templateLinkRepo
                        .findByTemplate_IdAndIsActiveTrueAndDocument_StatusAndDocument_IsActiveTrue(
                                templateId, DocumentStatus.PUBLISHED)
                        .stream())
                .map(DocumentTemplates::getDocument)
                .filter(previous -> !previous.getId().equals(doc.getId()))
                .distinct()
                .forEach(previous -> {
                    previous.setStatus(DocumentStatus.ARCHIVED);
                    documentRepo.save(previous);

                    auditService.log(AuditContext.builder()
                            .tableName("privacy_documents")
                            .recordId(previous.getId())
                            .action("DOCUMENT_ARCHIVED_POR_NUEVA_VERSION")
                            .oldData(Map.of("status", "PUBLISHED"))
                            .newData(Map.of("status", "ARCHIVED"))
                            .actorId(publishedBy)
                            .actorRole(securityContextHelper.getActorRole())
                            .build());
                });

        // Generar PDF en memoria y almacenar bytes + hash
        doc.setPublishAt(LocalDateTime.now());
        PdfGeneratorService.PdfResult result = pdfGenerator.generate(doc);

        doc.setPdfContent(result.pdfBytes());
        doc.setHashSha256(result.sha256Hash());
        doc.setStatus(DocumentStatus.PUBLISHED);

        log.info("Documento publicado: {} v{} | hash: {}", doc.getId(), doc.getVersion(), result.sha256Hash());
        PrivacyDocuments saved = documentRepo.save(doc);

        auditService.log(AuditContext.builder()
                .tableName("privacy_documents")
                .recordId(saved.getId())
                .action("DOCUMENT_PUBLISHED")
                .oldData(Map.of("status", "APPROVED"))
                .newData(Map.of(
                        "status",     String.valueOf(saved.getStatus()),
                        "version",    String.valueOf(saved.getVersion()),
                        "hashSha256", saved.getHashSha256()))
                .actorId(publishedBy)
                .actorRole(securityContextHelper.getActorRole())
                .build());

        // Notificar a los JEFE_DOMINIO de los dominios cubiertos por las finalidades del documento
        List<UUID> domainIds = purposeRepo.findByDocument_IdAndIsActiveTrue(doc.getId()).stream()
                .map(dp -> dp.getPurpose().getDomainId())
                .distinct()
                .toList();

        domainIds.forEach(domainId -> {
            String domainName = domainsRepo.findById(domainId)
                    .map(d -> d.getName())
                    .orElse(domainId.toString());
            userDomainRepository.findByDomain_Id(domainId).forEach(ud -> {
                notificationService.create(
                        ud.getKeycloakId(),
                        NotificationType.DOCUMENT_PUBLISHED,
                        "Documento publicado: " + doc.getName(),
                        "El DPO publicó el documento '" + doc.getName() + "' para el dominio " + domainName + ".",
                        doc.getId());
            });
        });

        // Notificar al solicitante original del PurposeRequest (trazabilidad del ticket)
        activePurposes.stream()
                .map(dp -> dp.getPurpose())
                .filter(p -> p.getPurposeRequestId() != null)
                .forEach(purpose -> purposeRequestsRepo.findById(purpose.getPurposeRequestId())
                        .ifPresent(pr -> notificationService.create(
                                pr.getRequesterId(),
                                NotificationType.PURPOSE_REQUEST_FULFILLED,
                                "Tu solicitud fue publicada: " + purpose.getName(),
                                "La finalidad '" + purpose.getName() + "' derivada de tu solicitud ha sido publicada en el documento '" + doc.getName() + "'.",
                                purpose.getId())));

        return PrivacyDocumentResponse.from(saved);
    }

    public PrivacyDocumentResponse archive(UUID id) {
        PrivacyDocuments doc = findOrThrow(id);
        validateTransition(doc.getStatus(), DocumentStatus.ARCHIVED);
        // No se bloquea por finalidades activas: publish() las requiere, y removePurpose() solo funciona
        // en DRAFT. El chequeo previo creaba un estado inalcanzable para documentos PUBLISHED.

        String previousStatus = String.valueOf(doc.getStatus());
        doc.setStatus(DocumentStatus.ARCHIVED);
        PrivacyDocuments saved = documentRepo.save(doc);

        auditService.log(AuditContext.builder()
                .tableName("privacy_documents")
                .recordId(saved.getId())
                .action("DOCUMENT_ARCHIVED")
                .oldData(Map.of("status", previousStatus))
                .newData(Map.of("status", String.valueOf(saved.getStatus())))
                .actorId(securityContextHelper.getKeycloakId())
                .actorRole(securityContextHelper.getActorRole())
                .build());

        return PrivacyDocumentResponse.from(saved);
    }

    // ── UTILIDADES ───────────────────────────────────────────────────────────────

    /**
     * Descarga el PDF binario del documento publicado.
     * Usado por GET /api/privacy-documents/{id}/pdf
     */
    @Transactional(readOnly = true)
    public byte[] downloadPdf(UUID id) {
        PrivacyDocuments doc = findOrThrow(id);
        if (doc.getPdfContent() == null || doc.getPdfContent().length == 0) {
            throw new BusinessValidationException(
                    "El documento no tiene PDF generado. Debe estar en estado PUBLISHED.");
        }
        return doc.getPdfContent();
    }

    /**
     * Verifica que el SHA-256 almacenado coincida con el hash real del PDF en BD.
     * Detecta corrupción o alteración del binario.
     */
    @Transactional(readOnly = true)
    public VerifyResponse verify(UUID id) {
        PrivacyDocuments doc = findOrThrow(id);

        if (doc.getPdfContent() == null || doc.getPdfContent().length == 0) {
            return VerifyResponse.builder()
                    .documentId(id).version(doc.getVersion())
                    .hashMatch(false)
                    .message("El documento no tiene PDF generado (no ha sido publicado)")
                    .build();
        }

        boolean match      = pdfGenerator.verify(doc.getPdfContent(), doc.getHashSha256());
        String currentHash = match
                ? doc.getHashSha256()
                : pdfGenerator.computeCurrentHash(doc.getPdfContent());

        return VerifyResponse.builder()
                .documentId(id).version(doc.getVersion())
                .hashMatch(match)
                .storedHash(doc.getHashSha256())
                .computedHash(currentHash)
                .message(match
                        ? "Integridad verificada: el PDF almacenado coincide con el hash registrado"
                        : "⚠ ALERTA: el hash del PDF no coincide — posible corrupción o alteración")
                .build();
    }

    /** Recalcula el hash del PDF sin chequeo de permisos — usado por IntegrityVerifier. */
    @Transactional(readOnly = true)
    public String recalculateHash(UUID id) {
        PrivacyDocuments doc = findOrThrow(id);
        if (doc.getPdfContent() == null || doc.getPdfContent().length == 0) {
            return null;
        }
        return pdfGenerator.computeCurrentHash(doc.getPdfContent());
    }

    @Transactional(readOnly = true)
    public PrivacyDocumentResponse getActive(DocumentCategory category) {
        return documentRepo.findTopByCategoryAndStatusAndIsActiveTrueOrderByVersionDesc(
                        category, DocumentStatus.PUBLISHED)
                .map(PrivacyDocumentResponse::from)
                .orElseThrow(() -> new DocumentNotFoundException(null));
    }

    public PrivacyDocumentResponse newVersion(UUID sourceId) {
        PrivacyDocuments source = findOrThrow(sourceId);

        if (source.getStatus() == DocumentStatus.ARCHIVED) {
            throw new BusinessValidationException(
                    "No se puede crear una nueva versión desde un documento archivado.");
        }

        UUID familyId = source.getDocumentFamilyId() != null
                ? source.getDocumentFamilyId()
                : source.getId();

        if (documentRepo.existsByDocumentFamilyIdAndStatusAndIsActiveTrue(
                familyId, DocumentStatus.DRAFT)) {
            throw new BusinessValidationException(
                    "Ya existe un borrador activo en esta familia de documentos. " +
                    "Publicá o desactivá el DRAFT existente antes de crear una nueva versión.");
        }

        int nextVersion = documentRepo
                .findByDocumentFamilyIdAndIsActiveTrueOrderByVersionDesc(familyId)
                .stream()
                .mapToInt(PrivacyDocuments::getVersion)
                .max()
                .orElse(source.getVersion()) + 1;

        String actorId = securityContextHelper.getKeycloakId();
        String actorName = securityContextHelper.getName();

        PrivacyDocuments newDoc = PrivacyDocuments.builder()
                .documentFamilyId(familyId)
                .category(source.getCategory())
                .status(DocumentStatus.DRAFT)
                .version(nextVersion)
                .name(source.getName())
                .content(source.getContent())
                .isActive(true)
                .createdBy(actorId)
                .createdByName(actorName)
                .build();

        PrivacyDocuments saved = documentRepo.save(newDoc);

        // Hereda los templates vinculados de la versión origen, igual que hacía con templateId
        templateLinkRepo.findByDocument_IdAndIsActiveTrue(sourceId).forEach(sourceLink -> {
            DocumentTemplates newLink = new DocumentTemplates();
            newLink.setId(new DocumentTemplates.DocumentTemplatesId(saved.getId(), sourceLink.getId().getTemplateId()));
            newLink.setDocument(saved);
            newLink.setTemplate(sourceLink.getTemplate());
            newLink.setIsActive(true);
            templateLinkRepo.save(newLink);
        });

        auditService.log(AuditContext.builder()
                .tableName("privacy_documents")
                .recordId(saved.getId())
                .action("DOCUMENT_NEW_VERSION_CREATED")
                .oldData(Map.of(
                        "sourceId", String.valueOf(sourceId),
                        "familyId", String.valueOf(familyId),
                        "sourceVersion", String.valueOf(source.getVersion())))
                .newData(Map.of(
                        "id",      String.valueOf(saved.getId()),
                        "version", String.valueOf(saved.getVersion()),
                        "status",  String.valueOf(saved.getStatus())))
                .actorId(actorId)
                .actorRole(securityContextHelper.getActorRole())
                .build());

        return PrivacyDocumentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<PrivacyDocumentResponse> getByFamily(UUID familyId) {
        boolean privileged = isPrivilegedUser();
        return documentRepo.findByDocumentFamilyIdAndIsActiveTrueOrderByVersionDesc(familyId)
                .stream()
                .filter(doc -> privileged || doc.getStatus() == DocumentStatus.PUBLISHED)
                .map(privileged ? PrivacyDocumentResponse::from : PrivacyDocumentResponse::fromPublic)
                .toList();
    }

    // ── Validaciones de negocio ───────────────────────────────────────────────────

    private void validatePurposeApproved(UUID purposeId) {
        var purpose = purposesRepo.findById(purposeId)
                .orElseThrow(() -> new PurposeNotFoundException(purposeId));
        if (!Boolean.TRUE.equals(purpose.getIsActive()) || purpose.getApprovedBy() == null) {
            throw new BusinessValidationException(
                    "La finalidad " + purposeId + " debe estar aprobada y activa para vincularse a un documento");
        }
    }

    private void validateReadyForReview(PrivacyDocuments doc) {
        if (doc.getContent() == null || doc.getContent().isBlank()) {
            throw new BusinessValidationException("El contenido no puede estar vacío antes de enviar a revisión");
        }
        List<DocumentPurposes> purposes = purposeRepo.findByDocument_IdAndIsActiveTrue(doc.getId());
        if (purposes.isEmpty()) {
            throw new BusinessValidationException("El documento debe tener al menos una finalidad activa vinculada");
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private PrivacyDocuments findOrThrow(UUID id) {
        return documentRepo.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));
    }

    private void requireStatus(PrivacyDocuments doc, DocumentStatus required) {
        if (doc.getStatus() != required) {
            throw new BusinessValidationException(
                    "La operación requiere estado " + required + " (actual: " + doc.getStatus() + ")");
        }
    }

    private boolean isPrivilegedUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_DPO") || a.getAuthority().equals("ROLE_ADMIN"));
    }

    private void validateTransition(DocumentStatus current, DocumentStatus target) {
        boolean valid = switch (current) {
            case DRAFT     -> target == DocumentStatus.IN_REVIEW;
            case IN_REVIEW -> target == DocumentStatus.APPROVED || target == DocumentStatus.REJECTED;
            case REJECTED  -> target == DocumentStatus.IN_REVIEW;
            case APPROVED  -> target == DocumentStatus.PUBLISHED;
            case PUBLISHED -> target == DocumentStatus.ARCHIVED;
            case ARCHIVED  -> false;
        };
        if (!valid) throw new InvalidTransitionException(current, target);
    }
}
