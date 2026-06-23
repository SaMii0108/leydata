package com.leydata.backend.orgdomain.web;

import com.leydata.backend.orgdomain.application.dto.CreateDomainRequest;
import com.leydata.backend.orgdomain.application.dto.DomainResponse;
import com.leydata.backend.orgdomain.application.service.DomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/domains")
@RequiredArgsConstructor
public class DomainController {

    private final DomainService domainService;

    // POST /api/domains — crear dominio (solo ADMIN)
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createDomain(@RequestBody CreateDomainRequest request) {
        DomainResponse created = domainService.createDomain(request);
        return Map.of(
                "status", "success",
                "message", "Dominio creado correctamente",
                "domainId", created.getId());
    }

    // GET /api/domains/all — listar todos incluidos inactivos (solo ADMIN)
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/all")
    public Map<String, Object> getAllDomains() {
        List<DomainResponse> domains = domainService.getAllDomains();
        return Map.of("status", "success", "domains", domains);
    }

    // POST /api/domains/{domainId}/deactivate — desactivar (solo ADMIN)
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{domainId}/deactivate")
    public Map<String, Object> deactivateDomain(@PathVariable UUID domainId) {
        DomainResponse domain = domainService.deactivateDomain(domainId);
        return Map.of(
                "status", "success",
                "message", "Dominio desactivado correctamente",
                "domainId", domain.getId());
    }

    // POST /api/domains/{domainId}/reactivate — reactivar (solo ADMIN)
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{domainId}/reactivate")
    public Map<String, Object> reactivateDomain(@PathVariable UUID domainId) {
        DomainResponse domain = domainService.reactivateDomain(domainId);
        return Map.of(
                "status", "success",
                "message", "Dominio reactivado correctamente",
                "domainId", domain.getId());
    }
}
