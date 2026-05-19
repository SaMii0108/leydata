package com.leydata.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PurposeRequestDto {
    private String title;
    private String justification;
    private String requestedData;
    private java.util.UUID domainId;
}
