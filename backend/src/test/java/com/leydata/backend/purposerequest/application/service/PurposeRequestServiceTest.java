package com.leydata.backend.purposerequest.application.service;

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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurposeRequestServiceTest {

    @Mock private PurposeRequestsRepository purposeRequestsRepository;
    @Mock private DomainsRepository domainsRepository;
    @Mock private AuditService auditService;
    @Mock private SecurityContextHelper securityContextHelper;
    @Mock private NotificationService notificationService;
    @Mock private UserDomainRepository userDomainRepository;

    @InjectMocks
    private PurposeRequestService service;

    private final UUID domainId = UUID.randomUUID();
    private final UUID requestId = UUID.randomUUID();
    private final String requesterId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        lenient().when(securityContextHelper.getKeycloakId()).thenReturn(requesterId);
        lenient().when(securityContextHelper.getName()).thenReturn("Jefe de Marketing");
        lenient().when(purposeRequestsRepository.save(any(PurposeRequests.class)))
                .thenAnswer(inv -> {
                    PurposeRequests pr = inv.getArgument(0);
                    if (pr.getId() == null) {
                        pr.setId(requestId);
                    }
                    return pr;
                });
    }

    private Domains activeDomain() {
        Domains d = new Domains();
        d.setId(domainId);
        d.setName("Marketing");
        d.setActive(true);
        return d;
    }

    private PurposeRequestDto requestDto() {
        PurposeRequestDto dto = new PurposeRequestDto();
        dto.setTitle("Newsletter de ofertas");
        dto.setJustification("Comunicar descuentos a clientes suscritos");
        dto.setRequestedData("Email, nombre");
        dto.setDomainId(domainId);
        return dto;
    }

    private PurposeRequests pendingRequest() {
        PurposeRequests pr = new PurposeRequests();
        pr.setId(requestId);
        pr.setDomainId(domainId);
        pr.setRequesterId(requesterId);
        pr.setTitle("Newsletter de ofertas");
        pr.setStatus("PENDING");
        return pr;
    }

    // ── createPurposeRequest() ──────────────────────────────────────────────────

    @Test
    void createPurposeRequest_creaSolicitudEnPendingConDatosDelJefeAutenticado() {
        when(domainsRepository.findById(domainId)).thenReturn(Optional.of(activeDomain()));
        when(userDomainRepository.existsByKeycloakIdAndDomainId(requesterId, domainId)).thenReturn(true);
        when(purposeRequestsRepository.existsByRequesterIdAndDomainIdAndTitleAndStatus(
                requesterId, domainId, "Newsletter de ofertas", "PENDING")).thenReturn(false);

        PurposeRequestSummaryDto result = service.createPurposeRequest(requestDto());

        assertThat(result.getStatus()).isEqualTo("PENDING");
        assertThat(result.getRequesterId()).isEqualTo(requesterId);
        assertThat(result.getDomainId()).isEqualTo(domainId);
    }

    @Test
    void createPurposeRequest_lanzaExcepcion_siElDominioNoExiste() {
        when(domainsRepository.findById(domainId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createPurposeRequest(requestDto()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createPurposeRequest_lanzaExcepcion_siElDominioEstaDesactivado() {
        Domains inactive = activeDomain();
        inactive.setActive(false);
        when(domainsRepository.findById(domainId)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.createPurposeRequest(requestDto()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void createPurposeRequest_lanzaExcepcion_siElJefeNoTieneAsignadoEseDominio() {
        when(domainsRepository.findById(domainId)).thenReturn(Optional.of(activeDomain()));
        when(userDomainRepository.existsByKeycloakIdAndDomainId(requesterId, domainId)).thenReturn(false);

        assertThatThrownBy(() -> service.createPurposeRequest(requestDto()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createPurposeRequest_lanzaExcepcion_siYaExisteUnaSolicitudPendienteConElMismoTitulo() {
        when(domainsRepository.findById(domainId)).thenReturn(Optional.of(activeDomain()));
        when(userDomainRepository.existsByKeycloakIdAndDomainId(requesterId, domainId)).thenReturn(true);
        when(purposeRequestsRepository.existsByRequesterIdAndDomainIdAndTitleAndStatus(
                requesterId, domainId, "Newsletter de ofertas", "PENDING")).thenReturn(true);

        assertThatThrownBy(() -> service.createPurposeRequest(requestDto()))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── getMyRequests() ──────────────────────────────────────────────────────────

    @Test
    void getMyRequests_devuelveSoloLasSolicitudesDelJefeAutenticado() {
        when(purposeRequestsRepository.findByRequesterId(requesterId)).thenReturn(List.of(pendingRequest()));

        List<PurposeRequestSummaryDto> result = service.getMyRequests();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRequesterId()).isEqualTo(requesterId);
    }

    // ── getPendingRequests() ─────────────────────────────────────────────────────

    @Test
    void getPendingRequests_requiereDpoOAdminYDevuelveSoloPendientes() {
        when(purposeRequestsRepository.findByStatus("PENDING")).thenReturn(List.of(pendingRequest()));

        List<PurposeRequestSummaryDto> result = service.getPendingRequests();

        verify(securityContextHelper).requireDpoOrAdmin();
        assertThat(result).hasSize(1);
    }

    // ── getAllRequests() ─────────────────────────────────────────────────────────

    @Test
    void getAllRequests_requiereDpoOAdminYDevuelveTodasIndependienteDelEstado() {
        when(purposeRequestsRepository.findAll()).thenReturn(List.of(pendingRequest()));

        List<PurposeRequestSummaryDto> result = service.getAllRequests();

        verify(securityContextHelper).requireDpoOrAdmin();
        assertThat(result).hasSize(1);
    }

    // ── reviewRequest() ──────────────────────────────────────────────────────────

    @Test
    void reviewRequest_aprueba_actualizaEstadoYNotificaAlJefe() {
        when(purposeRequestsRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest()));

        ReviewRequestDto review = new ReviewRequestDto();
        review.setStatus("APPROVED");

        PurposeRequestSummaryDto result = service.reviewRequest(requestId, review);

        assertThat(result.getStatus()).isEqualTo("APPROVED");
        verify(notificationService).create(
                requesterId, NotificationType.PURPOSE_APPROVED, "Solicitud aprobada: Newsletter de ofertas",
                "Tu solicitud fue aprobada por el DPO.", requestId);
    }

    @Test
    void reviewRequest_rechaza_conMotivoObligatorio_actualizaEstadoYNotifica() {
        when(purposeRequestsRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest()));

        ReviewRequestDto review = new ReviewRequestDto();
        review.setStatus("REJECTED");
        review.setReviewNotes("No cumple con la Ley 21.719");

        PurposeRequestSummaryDto result = service.reviewRequest(requestId, review);

        assertThat(result.getStatus()).isEqualTo("REJECTED");
        verify(notificationService).create(
                org.mockito.ArgumentMatchers.eq(requesterId),
                org.mockito.ArgumentMatchers.eq(NotificationType.PURPOSE_REJECTED),
                any(), any(), org.mockito.ArgumentMatchers.eq(requestId));
    }

    @Test
    void reviewRequest_lanzaExcepcion_siElStatusNoEsApprovedNiRejected() {
        ReviewRequestDto review = new ReviewRequestDto();
        review.setStatus("PENDING");

        assertThatThrownBy(() -> service.reviewRequest(requestId, review))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reviewRequest_lanzaExcepcion_siRechazaSinReviewNotes() {
        ReviewRequestDto review = new ReviewRequestDto();
        review.setStatus("REJECTED");

        assertThatThrownBy(() -> service.reviewRequest(requestId, review))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reviewRequest_lanzaExcepcion_siLaSolicitudNoExiste() {
        when(purposeRequestsRepository.findById(requestId)).thenReturn(Optional.empty());

        ReviewRequestDto review = new ReviewRequestDto();
        review.setStatus("APPROVED");

        assertThatThrownBy(() -> service.reviewRequest(requestId, review))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void reviewRequest_lanzaExcepcion_siLaSolicitudYaFueRevisada() {
        PurposeRequests reviewed = pendingRequest();
        reviewed.setStatus("APPROVED");
        when(purposeRequestsRepository.findById(requestId)).thenReturn(Optional.of(reviewed));

        ReviewRequestDto review = new ReviewRequestDto();
        review.setStatus("REJECTED");
        review.setReviewNotes("Motivo");

        assertThatThrownBy(() -> service.reviewRequest(requestId, review))
                .isInstanceOf(IllegalStateException.class);
    }
}
