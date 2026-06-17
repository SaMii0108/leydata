package com.leydata.backend.orgdomain.application.dto;

import com.leydata.backend.entity.Domains;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DomainResponse {
    private UUID id;
    private String code;
    private String name;
    private String description;
    private Boolean active;
    private LocalDateTime createdAt;

    public static DomainResponse from(Domains domain) {
        return new DomainResponse(
                domain.getId(),
                domain.getCode(),
                domain.getName(),
                domain.getDescription(),
                domain.getActive(),
                domain.getCreatedAt());
    }
}
