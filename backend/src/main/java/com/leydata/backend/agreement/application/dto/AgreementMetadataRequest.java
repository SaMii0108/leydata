package com.leydata.backend.agreement.application.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgreementMetadataRequest {
    private String captureChannel;
    private String signatureToken;
    private String authProvider;
    private String extraVariables;
}
