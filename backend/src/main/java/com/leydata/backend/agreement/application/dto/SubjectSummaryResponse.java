package com.leydata.backend.agreement.application.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.UUID;

@Value
@Builder
public class SubjectSummaryResponse {
    String subjectIdentifier;
    UUID domainId;
    UUID agreementId;
    UUID templateId;
    String templateKey;
    Integer templateVersion;
    UUID documentId;
    List<PurposeSummaryItem> purposes;
}
