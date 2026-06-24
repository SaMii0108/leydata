package com.leydata.backend.datacategory.application.service;

import com.leydata.backend.audit.application.dto.AuditContext;
import com.leydata.backend.audit.application.service.AuditService;
import com.leydata.backend.datacategory.application.dto.DataCategoryRequest;
import com.leydata.backend.datacategory.application.dto.DataCategoryResponse;
import com.leydata.backend.datacategory.domain.exception.DataCategoryNotFoundException;
import com.leydata.backend.datacategory.infrastructure.persistence.DataCategoryRepository;
import com.leydata.backend.entity.DataCategories;
import com.leydata.backend.privacydoc.domain.exception.BusinessValidationException;
import com.leydata.backend.purposedatacategory.infrastructure.persistence.PurposeDataCategoryRepository;
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
public class DataCategoryService {

    private final DataCategoryRepository repo;
    private final PurposeDataCategoryRepository pdcRepo;
    private final AuditService auditService;
    private final SecurityContextHelper securityContextHelper;

    @Transactional(readOnly = true)
    public List<DataCategoryResponse> listAll() {
        return repo.findByIsActiveTrue().stream()
                .map(DataCategoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DataCategoryResponse> listSensitive() {
        return repo.findByIsSensitiveAndIsActiveTrue(true).stream()
                .map(DataCategoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DataCategoryResponse getById(UUID id) {
        return DataCategoryResponse.from(findOrThrow(id));
    }

    public DataCategoryResponse create(DataCategoryRequest req) {
        if (repo.existsByCode(req.getCode().toUpperCase())) {
            throw new BusinessValidationException(
                    "Ya existe una categoría con código: " + req.getCode());
        }

        DataCategories entity = DataCategories.builder()
                .code(req.getCode().toUpperCase())
                .name(req.getName())
                .description(req.getDescription())
                .isSensitive(req.getIsSensitive())
                .isSystem(false)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();

        DataCategories saved = repo.save(entity);

        auditService.log(AuditContext.builder()
                .tableName("data_categories")
                .recordId(saved.getId())
                .action("DATA_CATEGORY_CREATED")
                .oldData(null)
                .newData(Map.of("code", saved.getCode(), "name", saved.getName(),
                        "isSensitive", saved.getIsSensitive()))
                .actorId(securityContextHelper.getAuthenticatedDpo().getId())
                .actorRole(securityContextHelper.getActorRole())
                .build());

        return DataCategoryResponse.from(saved);
    }

    public DataCategoryResponse update(UUID id, DataCategoryRequest req) {
        DataCategories entity = findOrThrow(id);

        if (Boolean.TRUE.equals(entity.getIsSystem())) {
            throw new BusinessValidationException(
                    "Las categorías del sistema (Ley 21.719) no pueden modificarse.");
        }

        String oldCode = entity.getCode();
        String oldName = entity.getName();

        if (req.getCode() != null && !req.getCode().toUpperCase().equals(entity.getCode())) {
            if (repo.existsByCode(req.getCode().toUpperCase())) {
                throw new BusinessValidationException(
                        "Ya existe una categoría con código: " + req.getCode());
            }
            entity.setCode(req.getCode().toUpperCase());
        }
        if (req.getName() != null)        entity.setName(req.getName());
        if (req.getDescription() != null) entity.setDescription(req.getDescription());
        if (req.getIsSensitive() != null) entity.setIsSensitive(req.getIsSensitive());

        DataCategories saved = repo.save(entity);

        auditService.log(AuditContext.builder()
                .tableName("data_categories")
                .recordId(saved.getId())
                .action("DATA_CATEGORY_UPDATED")
                .oldData(Map.of("code", oldCode, "name", oldName))
                .newData(Map.of("code", saved.getCode(), "name", saved.getName()))
                .actorId(securityContextHelper.getAuthenticatedDpo().getId())
                .actorRole(securityContextHelper.getActorRole())
                .build());

        return DataCategoryResponse.from(saved);
    }

    public DataCategoryResponse deactivate(UUID id) {
        DataCategories entity = findOrThrow(id);

        if (Boolean.TRUE.equals(entity.getIsSystem())) {
            throw new BusinessValidationException(
                    "Las categorías del sistema (Ley 21.719) no pueden desactivarse.");
        }

        if (pdcRepo.existsByDataCategoryId(id)) {
            throw new BusinessValidationException(
                    "La categoría '" + entity.getName() + "' está vinculada a una o más finalidades activas y no puede desactivarse. Desvincula primero la categoría de todas las finalidades.");
        }

        entity.setIsActive(false);
        DataCategories saved = repo.save(entity);

        auditService.log(AuditContext.builder()
                .tableName("data_categories")
                .recordId(saved.getId())
                .action("DATA_CATEGORY_DEACTIVATED")
                .oldData(Map.of("isActive", true))
                .newData(Map.of("isActive", false))
                .actorId(securityContextHelper.getAuthenticatedDpo().getId())
                .actorRole(securityContextHelper.getActorRole())
                .build());

        return DataCategoryResponse.from(saved);
    }

    private DataCategories findOrThrow(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new DataCategoryNotFoundException(id));
    }
}
