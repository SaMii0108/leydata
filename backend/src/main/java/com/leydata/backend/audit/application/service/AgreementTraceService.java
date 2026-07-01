package com.leydata.backend.audit.application.service;

import com.leydata.backend.agreement.domain.exception.AgreementNotFoundException;
import com.leydata.backend.agreement.infrastructure.persistence.AgreementsPurposesRepository;
import com.leydata.backend.agreement.infrastructure.persistence.AgreementsRepository;
import com.leydata.backend.audit.application.dto.AgreementTraceResponse;
import com.leydata.backend.audit.application.dto.AgreementTraceResponse.DocumentLink;
import com.leydata.backend.audit.application.dto.AgreementTraceResponse.PurposeLink;
import com.leydata.backend.audit.application.dto.AgreementTraceResponse.TemplateLink;
import com.leydata.backend.entity.Agreements;
import com.leydata.backend.entity.AgreementsPurposes;
import com.leydata.backend.entity.PrivacyDocuments;
import com.leydata.backend.entity.Purposes;
import com.leydata.backend.entity.Templates;
import com.leydata.backend.privacydoc.application.service.PrivacyDocumentService;
import com.leydata.backend.privacydoc.infrastructure.persistence.PrivacyDocumentsRepository;
import com.leydata.backend.purposes.infrastructure.persistence.PurposesRepository;
import com.leydata.backend.template.application.service.TemplateService;
import com.leydata.backend.template.infrastructure.persistence.TemplatesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

// Reconstruye la cadena AGREEMENT → DOCUMENT → TEMPLATE → PURPOSES con verificación de
// integridad por eslabón. Regla 13: solo lectura, no escribe en ningún log.
@Service
@RequiredArgsConstructor
public class AgreementTraceService {

    private enum LinkState { VALID, MISMATCH, UNKNOWN }

    private final AgreementsRepository agreementsRepo;
    private final AgreementsPurposesRepository agreementsPurposesRepo;
    private final PrivacyDocumentsRepository privacyDocumentsRepo;
    private final TemplatesRepository templatesRepo;
    private final PurposesRepository purposesRepo;

    private final PrivacyDocumentService privacyDocumentService;
    private final TemplateService templateService;

    @Transactional(readOnly = true)
    public AgreementTraceResponse trace(UUID agreementId) {
        Agreements agreement = agreementsRepo.findById(agreementId)
                .orElseThrow(() -> new AgreementNotFoundException(agreementId));

        List<LinkState> linkStates = new java.util.ArrayList<>();

        DocumentLink documentLink = buildDocumentLink(agreement.getDocumentId(), linkStates);
        TemplateLink templateLink = buildTemplateLink(agreement.getTemplateId(), linkStates);
        List<PurposeLink> purposeLinks = buildPurposeLinks(agreementId, linkStates);

        return AgreementTraceResponse.builder()
                .agreementId(agreement.getId())
                .dataSubjectId(agreement.getDataSubjectId())
                .createdAt(agreement.getCreatedAt())
                .document(documentLink)
                .template(templateLink)
                .purposes(purposeLinks)
                .overallIntegrity(overallIntegrity(linkStates))
                .build();
    }

    private DocumentLink buildDocumentLink(UUID documentId, List<LinkState> linkStates) {
        PrivacyDocuments document = privacyDocumentsRepo.findById(documentId).orElse(null);
        if (document == null) {
            linkStates.add(LinkState.UNKNOWN);
            return DocumentLink.builder().documentId(documentId).isValid(false).build();
        }

        LinkState state = resolveState(document.getHashSha256(),
                () -> privacyDocumentService.recalculateHash(documentId));
        linkStates.add(state);

        return DocumentLink.builder()
                .documentId(document.getId())
                .version(document.getVersion())
                .isValid(state == LinkState.VALID)
                .build();
    }

    private TemplateLink buildTemplateLink(UUID templateId, List<LinkState> linkStates) {
        Templates template = templatesRepo.findById(templateId).orElse(null);
        if (template == null) {
            linkStates.add(LinkState.UNKNOWN);
            return TemplateLink.builder().templateId(templateId).isValid(false).build();
        }

        LinkState state = resolveState(template.getHashSha256(),
                () -> templateService.recalculateHash(templateId));
        linkStates.add(state);

        return TemplateLink.builder()
                .templateId(template.getId())
                .templateKey(template.getTemplateKey())
                .version(template.getVersion())
                .isValid(state == LinkState.VALID)
                .build();
    }

    private List<PurposeLink> buildPurposeLinks(UUID agreementId, List<LinkState> linkStates) {
        return agreementsPurposesRepo.findByAgreementId(agreementId).stream()
                .map(ap -> buildPurposeLink(ap, linkStates))
                .toList();
    }

    private PurposeLink buildPurposeLink(AgreementsPurposes ap, List<LinkState> linkStates) {
        Purposes purpose = purposesRepo.findById(ap.getPurposeId()).orElse(null);
        if (purpose == null) {
            linkStates.add(LinkState.UNKNOWN);
            return PurposeLink.builder()
                    .purposeId(ap.getPurposeId())
                    .code(ap.getPurposeCode())
                    .accepted(ap.getAccepted())
                    .isValid(false)
                    .integrityStatus("UNKNOWN")
                    .build();
        }

        // El snapshot tomado al consentir (ap.getPurposeHash()) es el "stored hash" de este
        // eslabón — se compara contra el hash actual de la purpose para detectar drift
        // post-consentimiento.
        LinkState state = resolveState(ap.getPurposeHash(), () -> purpose.getHashSha256());
        linkStates.add(state);

        return PurposeLink.builder()
                .purposeId(purpose.getId())
                .purposeFamilyId(purpose.getPurposeFamilyId())
                .version(purpose.getVersion())
                .code(ap.getPurposeCode())
                .accepted(ap.getAccepted())
                .isValid(state == LinkState.VALID)
                .integrityStatus(toIntegrityStatus(state))
                .build();
    }

    private LinkState resolveState(String storedHash, java.util.function.Supplier<String> recalculate) {
        if (storedHash == null) {
            return LinkState.UNKNOWN;
        }
        String recalculated = recalculate.get();
        return Objects.equals(storedHash, recalculated) ? LinkState.VALID : LinkState.MISMATCH;
    }

    private String toIntegrityStatus(LinkState state) {
        return switch (state) {
            case VALID -> "OK";
            case MISMATCH -> "INTEGRITY_MISMATCH";
            case UNKNOWN -> "UNKNOWN";
        };
    }

    private String overallIntegrity(List<LinkState> linkStates) {
        if (linkStates.contains(LinkState.MISMATCH)) {
            return "MISMATCH";
        }
        if (linkStates.contains(LinkState.UNKNOWN)) {
            return "PARTIAL";
        }
        return "OK";
    }
}
