package com.leydata.backend.service;

import com.leydata.backend.dto.PurposeRequestDto;
import com.leydata.backend.dto.PurposeRequestSummaryDto;
import com.leydata.backend.dto.ReviewRequestDto;
import com.leydata.backend.entity.Domains;
import com.leydata.backend.entity.PurposeRequests;
import com.leydata.backend.entity.Users;
import com.leydata.backend.repository.DomainsRepository;
import com.leydata.backend.repository.PurposeRequestsRepository;
import com.leydata.backend.repository.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PurposeRequestService {

    private final PurposeRequestsRepository purposeRequestsRepository;
    private final UsersRepository usersRepository;
    private final DomainsRepository domainsRepository;

    // CREAR SOLICITUD JEFE_DOMINIO
    @Transactional
    public PurposeRequestSummaryDto createPurposeRequest(PurposeRequestDto request) {

        Users requester = getAuthenticatedUser();

        // Validar que el dominio exista y esté activo
        Domains domain = domainsRepository.findById(request.getDomainId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Dominio no encontrado: " + request.getDomainId()));

        if (!Boolean.TRUE.equals(domain.getActive())) {
            throw new IllegalStateException(
                    "El dominio está desactivado: " + domain.getName());
        }

        // Validar que el jefe pertenezca al dominio que solicita
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

        // Cargar relaciones para el DTO después de persistir
        saved.setDomain(domain);
        saved.setRequester(requester);

        return toSummaryDto(saved);
    }

    // MIS SOLICITUDES JEFE_DOMINIO ve solo las suyas
    @Transactional(readOnly = true)
    public List<PurposeRequestSummaryDto> getMyRequests() {
        Users requester = getAuthenticatedUser();
        return purposeRequestsRepository.findByRequesterId(requester.getId())
                .stream()
                .map(this::toSummaryDto)
                .toList();
    }

    // SOLICITUDES PENDIENTES DPO ve lo que tiene que revisar
    @Transactional(readOnly = true)
    public List<PurposeRequestSummaryDto> getPendingRequests() {
        getAuthenticatedDpo();
        return purposeRequestsRepository.findByStatus("PENDING")
                .stream()
                .map(this::toSummaryDto)
                .toList();
    }

    // TODAS LAS SOLICITUDES DPO ve historial completo
    @Transactional(readOnly = true)
    public List<PurposeRequestSummaryDto> getAllRequests() {
        getAuthenticatedDpo();
        return purposeRequestsRepository.findAll()
                .stream()
                .map(this::toSummaryDto)
                .toList();
    }

    // REVISAR SOLICITUD solo DPO (aprueba o rechaza)
    @Transactional
    public PurposeRequestSummaryDto reviewRequest(UUID requestId, ReviewRequestDto review) {

        Users dpo = getAuthenticatedDpo();

        // Validar estado
        if (!"APPROVED".equals(review.getStatus()) && !"REJECTED".equals(review.getStatus())) {
            throw new IllegalArgumentException("Estado inválido. Use APPROVED o REJECTED");
        }

        // Rechazo siempre requiere justificación — trazabilidad Ley 21.719
        if ("REJECTED".equals(review.getStatus()) &&
                (review.getReviewNotes() == null || review.getReviewNotes().isBlank())) {
            throw new IllegalArgumentException(
                    "El DPO debe justificar el rechazo en las notas de revisión (Ley 21.719)");
        }

        PurposeRequests purposeRequest = purposeRequestsRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Solicitud no encontrada: " + requestId));

        // Solo se pueden revisar solicitudes PENDING
        if (!"PENDING".equals(purposeRequest.getStatus())) {
            throw new IllegalStateException(
                    "La solicitud ya fue revisada. Estado actual: " + purposeRequest.getStatus());
        }

        purposeRequest.setStatus(review.getStatus());
        purposeRequest.setReviewerId(dpo.getId());
        purposeRequest.setReviewNotes(review.getReviewNotes());
        purposeRequest.setUpdatedAt(LocalDateTime.now());

        PurposeRequests saved = purposeRequestsRepository.save(purposeRequest);

        // Cargar relación del DPO para el DTO
        saved.setReviewer(dpo);

        return toSummaryDto(saved);
    }

    private Users getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return usersRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Usuario autenticado no encontrado"));
    }

    private Users getAuthenticatedDpo() {
        Users user = getAuthenticatedUser();
        boolean isDpo = user.getUserRoles().stream()
                .anyMatch(ur -> "DPO".equals(ur.getRole().getCode()));
        if (!isDpo) {
            throw new SecurityException(
                    "Acceso denegado: solo el DPO puede revisar solicitudes de consentimiento");
        }
        return user;
    }

    // Las relaciones lazy (domain, requester, reviewer) se acceden dentro
    // de la transacción activa — no hay riesgo de LazyInitializationException
    private PurposeRequestSummaryDto toSummaryDto(PurposeRequests pr) {
        return new PurposeRequestSummaryDto(
                pr.getId(),
                pr.getTitle(),
                pr.getJustification(),
                pr.getRequestedData(),

                // Dominio usa la relación si está cargada, fallback al UUID
                pr.getDomainId(),
                pr.getDomain() != null ? pr.getDomain().getName() : null,

                // Requester
                pr.getRequesterId(),
                pr.getRequester() != null ? pr.getRequester().getName() : null,

                // Estado
                pr.getStatus(),

                // Reviewer null si aún no fue revisada
                pr.getReviewerId(),
                pr.getReviewer() != null ? pr.getReviewer().getName() : null,
                pr.getReviewNotes(),

                pr.getCreatedAt(),
                pr.getUpdatedAt());
    }
}