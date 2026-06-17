package com.leydata.backend.purpose.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PurposeRequestDto {
    private String title;
    private String justification;
    private String requestedData;
    private UUID domainId;
}
