package com.leydata.backend.purposedatacategory.application.dto;

import com.leydata.backend.entity.DataRetentionPolicies;
import com.leydata.backend.entity.PurposeDataCategories;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class PurposeDataCategoryResponse {

    private UUID id;
    private UUID purposeId;

    // Categoría de datos
    private UUID dataCategoryId;
    private String dataCategoryCode;
    private String dataCategoryName;
    private Boolean isSensitive;
    private Boolean required;

    // Política de retención embebida
    private UUID retentionPolicyId;
    private Integer retentionPeriod;
    private String retentionUnit;
    private String legalJustification;
    private Boolean anonymizeAfter;

    // true cuando la finalidad está en un documento PUBLISHED — el DPO no puede modificar nada
    private Boolean retentionLocked;

    public static PurposeDataCategoryResponse from(PurposeDataCategories e, boolean locked) {
        DataRetentionPolicies ret = e.getDataRetentionPolicy();
        return PurposeDataCategoryResponse.builder()
                .id(e.getId())
                .purposeId(e.getPurposeId())
                .dataCategoryId(e.getDataCategoryId())
                .dataCategoryCode(e.getDataCategory() != null ? e.getDataCategory().getCode() : null)
                .dataCategoryName(e.getDataCategory() != null ? e.getDataCategory().getName() : null)
                .isSensitive(e.getDataCategory() != null ? e.getDataCategory().getIsSensitive() : null)
                .required(e.getRequired())
                .retentionPolicyId(ret != null ? ret.getId() : null)
                .retentionPeriod(ret != null ? ret.getRetentionPeriod() : null)
                .retentionUnit(ret != null ? ret.getRetentionUnit() : null)
                .legalJustification(ret != null ? ret.getLegalJustification() : null)
                .anonymizeAfter(ret != null ? ret.getAnonymizeAfter() : null)
                .retentionLocked(locked)
                .build();
    }
}
