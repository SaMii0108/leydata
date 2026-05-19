package com.leydata.backend.controller;

import com.leydata.backend.dto.CreateDomainRequest;
import com.leydata.backend.entity.Domains;
import com.leydata.backend.service.DomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    // POST /api/domains crear dominio (solo ADMIN)
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<?> createDomain(@RequestBody CreateDomainRequest request) {
        try {
            Domains created = domainService.createDomain(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "status", "success",
                    "message", "Dominio creado correctamente",
                    "domainId", created.getId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // // GET /api/domains listar dominios activos (cualquier rol autenticado)
    // @PreAuthorize("isAuthenticated()")
    // @GetMapping
    // public ResponseEntity<?> getActiveDomains() {
    // List<Domains> domains = domainService.getActiveDomains();
    // return ResponseEntity.ok(Map.of("status", "success", "domains", domains));
    // }

    // GET /api/domains/all listar todos incluidos inactivos (solo ADMIN)
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/all")
    public ResponseEntity<?> getAllDomains() {
        List<Domains> domains = domainService.getAllDomains();
        return ResponseEntity.ok(Map.of("status", "success", "domains", domains));
    }

    // POST /api/domains/{domainId}/deactivate desactivar (solo ADMIN)
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{domainId}/deactivate")
    public ResponseEntity<?> deactivateDomain(@PathVariable UUID domainId) {
        try {
            Domains domain = domainService.deactivateDomain(domainId);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Dominio desactivado correctamente",
                    "domainId", domain.getId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    // POST /api/domains/{domainId}/reactivate reactivar (solo ADMIN)
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{domainId}/reactivate")
    public ResponseEntity<?> reactivateDomain(@PathVariable UUID domainId) {
        try {
            Domains domain = domainService.reactivateDomain(domainId);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Dominio reactivado correctamente",
                    "domainId", domain.getId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }
}