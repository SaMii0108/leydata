package com.leydata.backend.purpose.application.service;

import com.leydata.backend.audit.application.dto.AuditContext;
import com.leydata.backend.audit.application.service.AuditService;
import com.leydata.backend.entity.Domains;
import com.leydata.backend.entity.PurposeRequests;
import com.leydata.backend.entity.Users;
import com.leydata.backend.notification.application.service.NotificationService;
import com.leydata.backend.notification.domain.enums.NotificationType;
import com.leydata.backend.orgdomain.infrastructure.persistence.DomainsRepository;
import com.leydata.backend.purpose.application.dto.PurposeRequestDto;
import com.leydata.backend.purpose.application.dto.PurposeRequestSummaryDto;
import com.leydata.backend.purpose.application.dto.ReviewRequestDto;
import com.leydata.backend.purpose.infrastructure.persistence.PurposeRequestsRepository;
import com.leydata.backend.shared.EmailService;
import com.leydata.backend.shared.SecurityContextHelper;
import com.leydata.backend.user.infrastructure.persistence.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PurposeRequestService {

    private final PurposeRequestsRepository purposeRequestsRepository;
    private final DomainsRepository domainsRepository;
    private final AuditService auditService;
    private final SecurityContextHelper securityContextHelper;
    private final NotificationService notificationService;
    private final Optional<EmailService> emailService;
    private final UsersRepository usersRepository;

    // CREAR SOLICITUD (JEFE_DOMINIO)
    @Transactional
    public PurposeRequestSummaryDto createPurposeRequest(PurposeRequestDto request) {
        Users requester = securityContextHelper.getAuthenticatedUser();

        Domains domain = domainsRepository.findById(request.getDomainId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Dominio no encontrado: " + request.getDomainId()));

        if (!Boolean.TRUE.equals(domain.getActive())) {
            throw new IllegalStateException("El dominio está desactivado: " + domain.getName());
        }

        // El jefe solo puede crear solicitudes para dominios que le pertenecen
        boolean ownsDomain = requester.getUserDomains().stream()
                .anyMatch(ud -> ud.getDomain().getId().equals(request.getDomainId()));
        if (!ownsDomain) {
            throw new IllegalArgumentException(
                    "No puedes crear solicitudes para un dominio que no te pertenece");
        }

        PurposeRequests purposeRequest = new PurposeRequests();
        purposeRequest.setDomainId(domain.getId());
        purposeRequest.setRequesterId(requester.getId());
        purposeRequest.setTitle(request.getTitle());
        purposeRequest.setJustification(request.getJustification());
        purposeRequest.setRequestedData(request.getRequestedData());
        purposeRequest.setStatus("PENDING");
        purposeRequest.setCreatedAt(LocalDateTime.now());

        PurposeRequests saved = purposeRequestsRepository.save(purposeRequest);
        saved.setDomain(domain);
        saved.setRequester(requester);

        auditService.log(AuditContext.builder()
                .tableName("purpose_requests")
                .recordId(saved.getId())
                .action("SOLICITAR_PROPOSITO")
                .oldData(null)
                .newData(Map.of(
                        "id", saved.getId(),
                        "title", saved.getTitle(),
                        "domainId", saved.getDomainId(),
                        "status", saved.getStatus()))
                .actorId(requester.getId())
                .actorRole(securityContextHelper.getActorRole())
                .build());

        return toSummaryDto(saved);
    }

    // MIS SOLICITUDES (JEFE_DOMINIO ve solo las propias)
    @Transactional(readOnly = true)
    public List<PurposeRequestSummaryDto> getMyRequests() {
        Users requester = securityContextHelper.getAuthenticatedUser();
        return purposeRequestsRepository.findByRequesterId(requester.getId())
                .stream()
                .map(this::toSummaryDto)
                .toList();
    }

    // SOLICITUDES PENDIENTES (DPO ve lo que debe revisar)
    @Transactional(readOnly = true)
    public List<PurposeRequestSummaryDto> getPendingRequests() {
        securityContextHelper.getAuthenticatedDpo();
        return purposeRequestsRepository.findByStatus("PENDING")
                .stream()
                .map(this::toSummaryDto)
                .toList();
    }

    // TODAS LAS SOLICITUDES (DPO ve el historial completo)
    @Transactional(readOnly = true)
    public List<PurposeRequestSummaryDto> getAllRequests() {
        securityContextHelper.getAuthenticatedDpo();
        return purposeRequestsRepository.findAll()
                .stream()
                .map(this::toSummaryDto)
                .toList();
    }

    // REVISAR SOLICITUD (solo DPO: aprueba o rechaza)
    @Transactional
    public PurposeRequestSummaryDto reviewRequest(UUID requestId, ReviewRequestDto review) {
        Users dpo = securityContextHelper.getAuthenticatedDpo();

        if (!"APPROVED".equals(review.getStatus()) && !"REJECTED".equals(review.getStatus())) {
            throw new IllegalArgumentException("Estado inválido. Use APPROVED o REJECTED");
        }

        // El rechazo siempre requiere justificación (Ley 21.719 art. 14 - principio de transparencia)
        if ("REJECTED".equals(review.getStatus()) &&
                (review.getReviewNotes() == null || review.getReviewNotes().isBlank())) {
            throw new IllegalArgumentException(
                    "El DPO debe justificar el rechazo en las notas de revisión (Ley 21.719)");
        }

        PurposeRequests purposeRequest = purposeRequestsRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Solicitud no encontrada: " + requestId));

        if (!"PENDING".equals(purposeRequest.getStatus())) {
            throw new IllegalStateException(
                    "La solicitud ya fue revisada. Estado actual: " + purposeRequest.getStatus());
        }

        String previousStatus = purposeRequest.getStatus();

        purposeRequest.setStatus(review.getStatus());
        purposeRequest.setReviewerId(dpo.getId());
        purposeRequest.setReviewNotes(review.getReviewNotes());
        purposeRequest.setUpdatedAt(LocalDateTime.now());

        PurposeRequests saved = purposeRequestsRepository.save(purposeRequest);
        saved.setReviewer(dpo);

        String action = "APPROVED".equals(review.getStatus()) ? "APROBAR_SOLICITUD" : "RECHAZAR_SOLICITUD";
        auditService.log(AuditContext.builder()
                .tableName("purpose_requests")
                .recordId(saved.getId())
                .action(action)
                .oldData(Map.of("status", previousStatus))
                .newData(Map.of(
                        "status", saved.getStatus(),
                        "reviewNotes", saved.getReviewNotes() != null ? saved.getReviewNotes() : ""))
                .actorId(dpo.getId())
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

        usersRepository.findById(saved.getRequesterId()).ifPresent(requester ->
                emailService.ifPresent(es -> es.sendPurposeReviewEmail(
                        requester.getEmail(),
                        requester.getName(),
                        saved.getTitle(),
                        review.getStatus(),
                        review.getReviewNotes())));

        return toSummaryDto(saved);
    }

    // Las relaciones lazy se acceden dentro de la transacción activa — sin riesgo de LazyInitializationException
    private PurposeRequestSummaryDto toSummaryDto(PurposeRequests pr) {
        return new PurposeRequestSummaryDto(
                pr.getId(),
                pr.getTitle(),
                pr.getJustification(),
                pr.getRequestedData(),
                pr.getDomainId(),
                pr.getDomain() != null ? pr.getDomain().getName() : null,
                pr.getRequesterId(),
                pr.getRequester() != null ? pr.getRequester().getName() : null,
                pr.getStatus(),
                pr.getReviewerId(),
                pr.getReviewer() != null ? pr.getReviewer().getName() : null,
                pr.getReviewNotes(),
                pr.getCreatedAt(),
                pr.getUpdatedAt());
    }
}
