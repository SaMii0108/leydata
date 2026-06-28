package com.leydata.backend.agreement.application.dto;

import com.leydata.backend.entity.AgreementMetadata;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class AgreementMetadataResponse {
    String ipOrigin;
    String userAgent;
    String captureChannel;
    String signatureToken;
    String authProvider;
    String extraVariables;
    LocalDateTime createdAt;

    public static AgreementMetadataResponse from(AgreementMetadata am) {
        return AgreementMetadataResponse.builder()
            .ipOrigin(am.getIpOrigin())
            .userAgent(am.getUserAgent())
            .captureChannel(am.getCaptureChannel())
            .signatureToken(am.getSignatureToken())
            .authProvider(am.getAuthProvider())
            .extraVariables(am.getExtraVariables())
            .createdAt(am.getCreatedAt())
            .build();
    }
}
