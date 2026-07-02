package com.leydata.backend.purposerequest.application.service;

import com.leydata.backend.audit.application.dto.AuditContext;
import com.leydata.backend.audit.application.service.AuditService;
import com.leydata.backend.entity.Domains;
import com.leydata.backend.entity.PurposeRequests;
import com.leydata.backend.notification.application.service.NotificationService;
import com.leydata.backend.notification.domain.enums.NotificationType;
import com.leydata.backend.orgdomain.infrastructure.persistence.DomainsRepository;
import com.leydata.backend.purposerequest.application.dto.PurposeRequestDto;
import com.leydata.backend.purposerequest.application.dto.PurposeRequestSummaryDto;
import com.leydata.backend.purposerequest.application.dto.ReviewRequestDto;
import com.leydata.backend.purposerequest.infrastructure.persistence.PurposeRequestsRepository;
import com.leydata.backend.shared.SecurityContextHelper;
import com.leydata.backend.userdomain.infrastructure.persistence.UserDomainRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PurposeRequestService {

    private final PurposeRequestsRepository purposeRequestsRepository;
    private final DomainsRepository domainsRepository;
    private final AuditService auditService;
    private final SecurityContextHelper securityContextHelper;
    private final NotificationService notificationService;
    private final UserDomainRepository userDomainRepository;

    // CREAR SOLICITUD (JEFE_DOMINIO)
    @Transactional
    public PurposeRequestSummaryDto createPurposeRequest(PurposeRequestDto request) {
        String requesterId = securityContextHelper.getKeycloakId();
        String requesterName = securityContextHelper.getName();

        Domains domain = domainsRepository.findById(request.getDomainId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Dominio no encontrado: " + request.getDomainId()));

        if (!Boolean.TRUE.equals(domain.getActive())) {
            throw new IllegalStateException("El dominio está desactivado: " + domain.getName());
        }

        boolean ownsDomain = userDomainRepository.existsByKeycloakIdAndDomainId(requesterId, request.getDomainId());
        if (!ownsDomain) {
            throw new IllegalArgumentException(
                    "No puedes crear solicitudes para un dominio que no te pertenece");
        }

        if (purposeRequestsRepository.existsByRequesterIdAndDomainIdAndTitleAndStatus(
                requesterId, domain.getId(), request.getTitle(), "PENDING")) {
            throw new IllegalStateException(
                    "Ya tienes una solicitud pendiente con el título '" + request.getTitle() +
                    "' para este dominio. Espera la revisión del DPO antes de reenviarla.");
        }

        PurposeRequests purposeRequest = new PurposeRequests();
        purposeRequest.setDomainId(domain.getId());
        purposeRequest.setRequesterId(requesterId);
        purposeRequest.setRequesterName(requesterName);
        purposeRequest.setTitle(request.getTitle());
        purposeRequest.setJustification(request.getJustification());
        purposeRequest.setRequestedData(request.getRequestedData());
        purposeRequest.setStatus("PENDING");
        purposeRequest.setCreatedAt(LocalDateTime.now());

        PurposeRequests saved = purposeRequestsRepository.save(purposeRequest);
        saved.setDomain(domain);

        Map<String, Object> auditData = new java.util.LinkedHashMap<>();
        auditData.put("id", saved.getId());
        auditData.put("title", saved.getTitle());
        auditData.put("domainId", saved.getDomainId());
        auditData.put("status", saved.getStatus());
        auditService.log(AuditContext.builder()
                .tableName("purpose_requests")
                .recordId(saved.getId())
                .action("SOLICITAR_PROPOSITO")
                .oldData(null)
                .newData(auditData)
                .actorId(requesterId)
                .actorRole(securityContextHelper.getActorRole())
                .build());

        return toSummaryDto(saved);
    }

    // MIS SOLICITUDES (JEFE_DOMINIO ve solo las propias)
    @Transactional(readOnly = true)
    public List<PurposeRequestSummaryDto> getMyRequests() {
        String requesterId = securityContextHelper.getKeycloakId();
        return purposeRequestsRepository.findByRequesterId(requesterId)
                .stream()
                .map(this::toSummaryDto)
                .toList();
    }

    // SOLICITUDES PENDIENTES (DPO ve lo que debe revisar)
    @Transactional(readOnly = true)
    public List<PurposeRequestSummaryDto> getPendingRequests() {
        securityContextHelper.requireDpoOrAdmin();
        return purposeRequestsRepository.findByStatus("PENDING")
                .stream()
                .map(this::toSummaryDto)
                .toList();
    }

    // TODAS LAS SOLICITUDES (DPO ve el historial completo)
    @Transactional(readOnly = true)
    public List<PurposeRequestSummaryDto> getAllRequests() {
        securityContextHelper.requireDpoOrAdmin();
        return purposeRequestsRepository.findAll()
                .stream()
                .map(this::toSummaryDto)
                .toList();
    }

    // REVISAR SOLICITUD (solo DPO: aprueba o rechaza)
    @Transactional
    public PurposeRequestSummaryDto reviewRequest(UUID requestId, ReviewRequestDto review) {
        securityContextHelper.requireDpoOrAdmin();
        String reviewerId = securityContextHelper.getKeycloakId();
        String reviewerName = securityContextHelper.getName();

        if (!"APPROVED".equals(review.getStatus()) && !"REJECTED".equals(review.getStatus())) {
            throw new IllegalArgumentException("Estado inválido. Use APPROVED o REJECTED");
        }

        if ("REJECTED".equals(review.getStatus()) &&
                (review.getReviewNotes() == null || review.getReviewNotes().isBlank())) {
            throw new IllegalArgumentException(
                    "El DPO debe justificar el rechazo en las notas de revisión (Ley 21.719)");
        }

        PurposeRequests purposeRequest = purposeRequestsRepository.findById(requestId)
                .orElseThrow(() -> new java.util.NoSuchElementException(
                        "Solicitud no encontrada: " + requestId));

        if (!"PENDING".equals(purposeRequest.getStatus())) {
            throw new IllegalStateException(
                    "La solicitud ya fue revisada. Estado actual: " + purposeRequest.getStatus());
        }

        String previousStatus = purposeRequest.getStatus();

        purposeRequest.setStatus(review.getStatus());
        purposeRequest.setReviewerId(reviewerId);
        purposeRequest.setReviewerName(reviewerName);
        purposeRequest.setReviewNotes(review.getReviewNotes());
        purposeRequest.setUpdatedAt(LocalDateTime.now());

        PurposeRequests saved = purposeRequestsRepository.save(purposeRequest);

        String action = "APPROVED".equals(review.getStatus()) ? "APROBAR_SOLICITUD" : "RECHAZAR_SOLICITUD";
        auditService.log(AuditContext.builder()
                .tableName("purpose_requests")
                .recordId(saved.getId())
                .action(action)
                .oldData(Map.of("status", previousStatus))
                .newData(Map.of(
                        "status", saved.getStatus(),
                        "reviewNotes", saved.getReviewNotes() != null ? saved.getReviewNotes() : ""))
                .actorId(reviewerId)
                .actorRole(securityContextHelper.getActorRole())
                .build());

        // Notificar al JEFE_DOMINIO que creó la solicitud
        boolean approved = "APPROVED".equals(review.getStatus());
        NotificationType notifType = approved ? NotificationType.PURPOSE_APPROVED : NotificationType.PURPOSE_REJECTED;
        String notifTitle = approved
                ? "Solicitud aprobada: " + saved.getTitle()
                : "Solicitud rechazada: " + saved.getTitle();
        String notifMsg = approved
                ? "Tu solicitud fue aprobada por el DPO."
                : "Tu solicitud fue rechazada. Motivo: " + (review.getReviewNotes() != null ? review.getReviewNotes() : "");
        notificationService.create(saved.getRequesterId(), notifType, notifTitle, notifMsg, saved.getId());

        return toSummaryDto(saved);
    }

    private PurposeRequestSummaryDto toSummaryDto(PurposeRequests pr) {
        return new PurposeRequestSummaryDto(
                pr.getId(),
                pr.getTitle(),
                pr.getJustification(),
                pr.getRequestedData(),
                pr.getDomainId(),
                pr.getDomain() != null ? pr.getDomain().getName() : null,
                pr.getRequesterId(),
                pr.getRequesterName(),
                pr.getStatus(),
                pr.getReviewerId(),
                pr.getReviewerName(),
                pr.getReviewNotes(),
                pr.getCreatedAt(),
                pr.getUpdatedAt());
    }
}
