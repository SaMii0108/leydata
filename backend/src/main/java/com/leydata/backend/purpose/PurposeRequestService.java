package com.leydata.backend.purpose;

import com.leydata.backend.audit.AuditContext;
import com.leydata.backend.audit.AuditService;
import com.leydata.backend.entity.Domains;
import com.leydata.backend.entity.PurposeRequests;
import com.leydata.backend.entity.Users;
import com.leydata.backend.domain.DomainsRepository;
import com.leydata.backend.user.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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
        private final UsersRepository usersRepository;
        private final DomainsRepository domainsRepository;
        private final AuditService auditService;

        // CREAR SOLICITUD (JEFE_DOMINIO)
        @Transactional
        public PurposeRequestSummaryDto createPurposeRequest(PurposeRequestDto request) {
                Users requester = getAuthenticatedUser();

                // Validar que el dominio exista y esté activo
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

                // Registrar que el JEFE_DOMINIO inició una solicitud de propósito
                auditService.tryLog(AuditContext.builder()
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
                                .actorRole(getActorRole())
                                .build());

                return toSummaryDto(saved);
        }

        // MIS SOLICITUDES (JEFE_DOMINIO ve solo las propias)
        @Transactional(readOnly = true)
        public List<PurposeRequestSummaryDto> getMyRequests() {
                Users requester = getAuthenticatedUser();
                return purposeRequestsRepository.findByRequesterId(requester.getId())
                                .stream()
                                .map(this::toSummaryDto)
                                .toList();
        }

        // SOLICITUDES PENDIENTES (DPO ve lo que debe revisar)
        @Transactional(readOnly = true)
        public List<PurposeRequestSummaryDto> getPendingRequests() {
                getAuthenticatedDpo();
                return purposeRequestsRepository.findByStatus("PENDING")
                                .stream()
                                .map(this::toSummaryDto)
                                .toList();
        }

        // TODAS LAS SOLICITUDES (DPO ve el historial completo)
        @Transactional(readOnly = true)
        public List<PurposeRequestSummaryDto> getAllRequests() {
                getAuthenticatedDpo();
                return purposeRequestsRepository.findAll()
                                .stream()
                                .map(this::toSummaryDto)
                                .toList();
        }

        // REVISAR SOLICITUD (solo DPO: aprueba o rechaza)
        @Transactional
        public PurposeRequestSummaryDto reviewRequest(UUID requestId, ReviewRequestDto review) {
                Users dpo = getAuthenticatedDpo();

                if (!"APPROVED".equals(review.getStatus()) && !"REJECTED".equals(review.getStatus())) {
                        throw new IllegalArgumentException("Estado inválido. Use APPROVED o REJECTED");
                }

                // El rechazo siempre requiere justificación (Ley 21.719 art. 14 - principio de
                // transparencia)
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

                // Guardar estado anterior para la auditoría
                String previousStatus = purposeRequest.getStatus();

                purposeRequest.setStatus(review.getStatus());
                purposeRequest.setReviewerId(dpo.getId());
                purposeRequest.setReviewNotes(review.getReviewNotes());
                purposeRequest.setUpdatedAt(LocalDateTime.now());

                PurposeRequests saved = purposeRequestsRepository.save(purposeRequest);
                saved.setReviewer(dpo);

                // La acción del DPO queda registrada: APROBAR o RECHAZAR una solicitud de
                // propósito
                String action = "APPROVED".equals(review.getStatus()) ? "APROBAR_SOLICITUD" : "RECHAZAR_SOLICITUD";
                auditService.tryLog(AuditContext.builder()
                                .tableName("purpose_requests")
                                .recordId(saved.getId())
                                .action(action)
                                .oldData(Map.of("status", previousStatus))
                                .newData(Map.of(
                                                "status", saved.getStatus(),
                                                "reviewNotes",
                                                saved.getReviewNotes() != null ? saved.getReviewNotes() : ""))
                                .actorId(dpo.getId())
                                .actorRole(getActorRole())
                                .build());

                return toSummaryDto(saved);
        }

        // HELPERS DE AUTENTICACIÓN

        // Retorna el usuario autenticado desde nuestra BD usando el email del JWT
        private Users getAuthenticatedUser() {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                // auth.getName() retorna el email (configurado en KeycloakJwtAuthConverter)
                return usersRepository.findByEmail(auth.getName())
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "Usuario autenticado no encontrado en el sistema"));
        }

        // Verifica que el usuario autenticado tiene rol DPO o ADMIN (desde el JWT de
        // Keycloak)
        private Users getAuthenticatedDpo() {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();

                // Los roles vienen del JWT de Keycloak, no de la BD
                boolean isDpoOrAdmin = auth.getAuthorities().stream()
                                .anyMatch(a -> "ROLE_DPO".equals(a.getAuthority())
                                                || "ROLE_ADMIN".equals(a.getAuthority()));

                if (!isDpoOrAdmin) {
                        throw new SecurityException(
                                        "Acceso denegado: solo el DPO puede revisar solicitudes de consentimiento");
                }

                return usersRepository.findByEmail(auth.getName())
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "Usuario autenticado no encontrado en el sistema"));
        }

        // Extrae el rol de negocio del JWT de Keycloak (ADMIN, DPO o JEFE_DOMINIO).
        // Filtra roles técnicos de Keycloak como offline_access, uma_authorization,
        // etc.
        private static final java.util.Set<String> BUSINESS_ROLES = java.util.Set.of("ADMIN", "DPO", "JEFE_DOMINIO",
                        "USER", "TITULAR");

        private String getActorRole() {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                return auth.getAuthorities().stream()
                                .map(GrantedAuthority::getAuthority)
                                .filter(a -> a.startsWith("ROLE_"))
                                .map(a -> a.substring(5))
                                .filter(BUSINESS_ROLES::contains)
                                .findFirst()
                                .orElse("UNKNOWN");
        }

        // CONVERSIÓN A DTO

        // Las relaciones lazy se acceden dentro de la transacción activa — sin riesgo
        // de LazyInitializationException
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
