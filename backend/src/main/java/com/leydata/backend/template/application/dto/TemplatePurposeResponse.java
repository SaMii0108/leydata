package com.leydata.backend.template.application.dto;

import java.util.UUID;

import com.leydata.backend.entity.TemplatePurposes;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TemplatePurposeResponse {

    UUID purposeId;
    String purposeName;
    Integer orderPosition;
    Boolean isVisible;

<<<<<<< HEAD
    public static TemplatePurposeResponse from(TemplatePurposes tp) {
=======
    public static TemplatePurposeResponse from(TemplatePurposes tp){
>>>>>>> origin/feature/templates-consentimiento
        return TemplatePurposeResponse.builder()
            .purposeId(tp.getPurpose().getId())
            .purposeName(tp.getPurpose().getName())
            .orderPosition(tp.getOrderPosition())
            .isVisible(tp.getIsVisible())
            .build();
    }
<<<<<<< HEAD
=======

>>>>>>> origin/feature/templates-consentimiento
}
