package com.leydata.backend.controller;

import com.leydata.backend.dto.CreateDomainRequest;
import com.leydata.backend.entity.Domains;
import com.leydata.backend.service.DomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/domains")
@RequiredArgsConstructor
public class DomainController {

    private final DomainService domainService;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<Domains> createDomain(@RequestBody CreateDomainRequest request) {
        try {
            Domains createdDomain = domainService.createDomain(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdDomain);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
}