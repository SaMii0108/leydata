package com.leydata.backend.purposedatacategory.application.service;

import com.leydata.backend.audit.application.dto.AuditContext;
import com.leydata.backend.audit.application.service.AuditService;
import com.leydata.backend.datacategory.infrastructure.persistence.DataCategoryRepository;
import com.leydata.backend.entity.DataRetentionPolicies;
import com.leydata.backend.entity.PurposeDataCategories;
import com.leydata.backend.privacydoc.domain.enums.DocumentStatus;
import com.leydata.backend.privacydoc.domain.exception.BusinessValidationException;
import com.leydata.backend.privacydoc.infrastructure.persistence.DocumentPurposesRepository;
import com.leydata.backend.purposedatacategory.application.dto.DataRetentionPolicyRequest;
import com.leydata.backend.purposedatacategory.application.dto.PurposeDataCategoryRequest;
import com.leydata.backend.purposedatacategory.application.dto.PurposeDataCategoryResponse;
import com.leydata.backend.purposedatacategory.domain.exception.PurposeDataCategoryNotFoundException;
import com.leydata.backend.purposedatacategory.domain.exception.RetentionPolicyLockedException;
import com.leydata.backend.purposedatacategory.infrastructure.persistence.PurposeDataCategoryRepository;
import com.leydata.backend.purposedatacategory.infrastructure.persistence.RetentionPolicyRepository;
import com.leydata.backend.purposes.infrastructure.persistence.PurposesRepository;
import com.leydata.backend.shared.SecurityContextHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class PurposeDataCategoryService {

    private final PurposeDataCategoryRepository pdcRepo;
    private final RetentionPolicyRepository retentionRepo;
    private final DataCategoryRepository dataCategoryRepo;
    private final PurposesRepository purposesRepo;
    private final DocumentPurposesRepository documentPurposesRepo;
    private final AuditService auditService;
    private final SecurityContextHelper securityContextHelper;

    // ── CONSULTA ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PurposeDataCategoryResponse> listByPurpose(UUID purposeId) {
        return pdcRepo.findByPurposeId(purposeId).stream()
                .map(pdc -> PurposeDataCategoryResponse.from(pdc, isLocked(purposeId)))
                .toList();
    }

    @Transactional(readOnly = true)
    public PurposeDataCategoryResponse getById(UUID id) {
        PurposeDataCategories pdc = findOrThrow(id);
        return PurposeDataCategoryResponse.from(pdc, isLocked(pdc.getPurposeId()));
    }

    // ── VINCULAR categoría a finalidad ────────────────────────────────────────────

    public PurposeDataCategoryResponse link(UUID purposeId, PurposeDataCategoryRequest req) {
        validatePurposeApproved(purposeId);
        validateDataCategoryActive(req.getDataCategoryId());
        // Agregar categorías a una finalidad en doc PUBLISHED amplía el alcance del
        // consentimiento sin que el titular lo haya visto — requiere nueva versión del doc.
        enforceRetentionNotLocked(purposeId);

        if (pdcRepo.existsByPurposeIdAndDataCategoryId(purposeId, req.getDataCategoryId())) {
            throw new BusinessValidationException(
                    "Esta categoría de datos ya está vinculada a la finalidad.");
        }

        PurposeDataCategories pdc = new PurposeDataCategories();
        pdc.setPurposeId(purposeId);
        pdc.setDataCategoryId(req.getDataCategoryId());
        pdc.setRequired(req.getRequired());
        pdc.setDataUses(req.getDataUses());
        PurposeDataCategories saved = pdcRepo.save(pdc);

        // La política de retención se crea junto con el vínculo — nunca puede quedar sin definir
        DataRetentionPolicies retention = buildRetention(saved.getId(), req.getRetention());
        retentionRepo.save(retention);
        // Setear en memoria: @OneToOne(mappedBy=...) no actualiza saved en la caché L1 de Hibernate
        saved.setDataRetentionPolicy(retention);

        auditService.log(AuditContext.builder()
                .tableName("purpose_data_categories")
                .recordId(saved.getId())
                .action("PURPOSE_DATA_CATEGORY_LINKED")
                .oldData(null)
                .newData(Map.of(
                        "purposeId",       purposeId.toString(),
                        "dataCategoryId",  req.getDataCategoryId().toString(),
                        "dataUses",        req.getDataUses().toString(),
                        "retentionPeriod", req.getRetention().getRetentionPeriod(),
                        "retentionUnit",   req.getRetention().getRetentionUnit()))
                .actorId(securityContextHelper.getKeycloakId())
                .actorRole(securityContextHelper.getActorRole())
                .build());

        return PurposeDataCategoryResponse.from(findOrThrow(saved.getId()), false);
    }

    // ── ACTUALIZAR política de retención — OPCIÓN B ───────────────────────────────
    // Bloqueado si la finalidad está en cualquier documento PUBLISHED.
    // Para cambiar la retención, el DPO debe crear una nueva versión del documento.

    public PurposeDataCategoryResponse updateRetention(UUID purposeDataCategoryId,
                                                       DataRetentionPolicyRequest req) {
        PurposeDataCategories pdc = findOrThrow(purposeDataCategoryId);

        enforceRetentionNotLocked(pdc.getPurposeId());

        DataRetentionPolicies retention = retentionRepo
                .findByPurposeDataCategoryId(purposeDataCategoryId)
                .orElseGet(() -> buildRetention(purposeDataCategoryId, req));

        Map<String, Object> oldData = Map.of(
                "retentionPeriod", retention.getRetentionPeriod(),
                "retentionUnit",   retention.getRetentionUnit());

        retention.setRetentionPeriod(req.getRetentionPeriod());
        retention.setRetentionUnit(req.getRetentionUnit());
        if (req.getLegalJustification() != null) retention.setLegalJustification(req.getLegalJustification());
        retention.setAnonymizeAfter(req.getAnonymizeAfter());
        retentionRepo.save(retention);

        auditService.log(AuditContext.builder()
                .tableName("data_retention_policies")
                .recordId(retention.getId())
                .action("RETENTION_POLICY_UPDATED")
                .oldData(oldData)
                .newData(Map.of(
                        "retentionPeriod", req.getRetentionPeriod(),
                        "retentionUnit",   req.getRetentionUnit()))
                .actorId(securityContextHelper.getKeycloakId())
                .actorRole(securityContextHelper.getActorRole())
                .build());

        return PurposeDataCategoryResponse.from(findOrThrow(purposeDataCategoryId), false);
    }

    // ── DESVINCULAR categoría de finalidad ────────────────────────────────────────
    // También bloqueado si hay documento PUBLISHED — el documento declara ese dato.

    public void unlink(UUID purposeDataCategoryId) {
        PurposeDataCategories pdc = findOrThrow(purposeDataCategoryId);

        enforceRetentionNotLocked(pdc.getPurposeId());

        auditService.log(AuditContext.builder()
                .tableName("purpose_data_categories")
                .recordId(purposeDataCategoryId)
                .action("PURPOSE_DATA_CATEGORY_UNLINKED")
                .oldData(Map.of(
                        "purposeId",      pdc.getPurposeId().toString(),
                        "dataCategoryId", pdc.getDataCategoryId().toString()))
                .newData(null)
                .actorId(securityContextHelper.getKeycloakId())
                .actorRole(securityContextHelper.getActorRole())
                .build());

        // Elimina también la política de retención 1:1
        retentionRepo.findByPurposeDataCategoryId(purposeDataCategoryId)
                .ifPresent(retentionRepo::delete);
        pdcRepo.delete(pdc);
    }

    // ── OPCIÓN B: verificación de bloqueo ────────────────────────────────────────

    private boolean isLocked(UUID purposeId) {
        return documentPurposesRepo
                .existsByPurpose_IdAndDocument_StatusAndIsActiveTrue(purposeId, DocumentStatus.PUBLISHED);
    }

    private void enforceRetentionNotLocked(UUID purposeId) {
        if (isLocked(purposeId)) {
            String purposeName = purposesRepo.findById(purposeId)
                    .map(p -> p.getName())
                    .orElse(purposeId.toString());
            throw new RetentionPolicyLockedException(purposeName);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private PurposeDataCategories findOrThrow(UUID id) {
        return pdcRepo.findById(id)
                .orElseThrow(() -> new PurposeDataCategoryNotFoundException(id));
    }

    private void validatePurposeApproved(UUID purposeId) {
        var purpose = purposesRepo.findById(purposeId)
                .orElseThrow(() -> new BusinessValidationException("Finalidad no encontrada: " + purposeId));
        if (!Boolean.TRUE.equals(purpose.getIsActive()) || purpose.getApprovedBy() == null) {
            throw new BusinessValidationException(
                    "La finalidad debe estar aprobada y activa para vincular categorías de datos.");
        }
    }

    private void validateDataCategoryActive(UUID dataCategoryId) {
        var cat = dataCategoryRepo.findById(dataCategoryId)
                .orElseThrow(() -> new BusinessValidationException("Categoría de datos no encontrada: " + dataCategoryId));
        if (!Boolean.TRUE.equals(cat.getIsActive())) {
            throw new BusinessValidationException(
                    "La categoría de datos '" + cat.getName() + "' está inactiva.");
        }
    }

    private DataRetentionPolicies buildRetention(UUID purposeDataCategoryId, DataRetentionPolicyRequest req) {
        DataRetentionPolicies ret = new DataRetentionPolicies();
        ret.setPurposeDataCategoryId(purposeDataCategoryId);
        ret.setRetentionPeriod(req.getRetentionPeriod());
        ret.setRetentionUnit(req.getRetentionUnit());
        ret.setLegalJustification(req.getLegalJustification());
        ret.setAnonymizeAfter(req.getAnonymizeAfter());
        ret.setIsActive(true);
        ret.setCreatedAt(LocalDateTime.now());
        return ret;
    }
}
