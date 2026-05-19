package com.leydata.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserSummaryDto {
    private UUID id;
    private String email;
    private String name;
    private Boolean active;
    private Boolean blocked;
    private List<String> roles;
    private List<String> domains;
}