package com.leydata.backend.agreement.application.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VerifyIntegrityRequest {
    private String checkType = "MANUAL"; // MANUAL, ON_DEMAND (SCHEDULED lo usa un job, no este endpoint)
}
