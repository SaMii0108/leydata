package com.leydata.backend.orgdomain.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateDomainRequest {
    private String code;
    private String name;
    private String description;
    private UUID jefeId; // Puede ser null
}
