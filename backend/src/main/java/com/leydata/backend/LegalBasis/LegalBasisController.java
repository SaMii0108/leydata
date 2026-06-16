package com.leydata.backend.LegalBasis;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/legal-basis")
@RequiredArgsConstructor
public class LegalBasisController {

    private final LegalBasisCatalogRepository legalBasisCatalogRepository;

    @PreAuthorize("hasAnyRole('ADMIN', 'DPO')")
    @GetMapping
    public ResponseEntity<?> getActiveLegalBases() {
        List<LegalBasisResponseDto> result = legalBasisCatalogRepository
                .findByIsActiveTrue()
                .stream()
                .map(LegalBasisResponseDto::from)
                .toList();
        return ResponseEntity.ok(Map.of("status", "success", "legalBases", result));
    }
}
