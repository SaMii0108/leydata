package com.leydata.backend.legalbasis.web;

import com.leydata.backend.legalbasis.application.dto.LegalBasisResponse;
import com.leydata.backend.legalbasis.infrastructure.persistence.LegalBasisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Catálogo de solo lectura — las bases de licitud las define la ley, no el DPO.
@RestController
@RequestMapping("/api/legal-basis")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('DPO','ADMIN','JEFE_DOMINIO')")
public class LegalBasisController {

    private final LegalBasisRepository repo;

    // GET /api/legal-basis — todas las bases activas
    @GetMapping
    public List<LegalBasisResponse> listAll() {
        return repo.findByIsActiveTrue().stream()
                .map(LegalBasisResponse::from)
                .toList();
    }

    // GET /api/legal-basis/consent — solo las que requieren consentimiento explícito
    @GetMapping("/consent")
    public List<LegalBasisResponse> listConsentBased() {
        return repo.findByConsentRequiredAndIsActiveTrue(true).stream()
                .map(LegalBasisResponse::from)
                .toList();
    }

    // GET /api/legal-basis/{id}
    @GetMapping("/{id}")
    public LegalBasisResponse getById(@PathVariable UUID id) {
        return repo.findById(id)
                .map(LegalBasisResponse::from)
                .orElseThrow(() -> new java.util.NoSuchElementException("Base de licitud no encontrada: " + id));
    }
}
