package com.leydata.backend.purposes.web;

import com.leydata.backend.purposes.application.dto.CreatePurposeRequest;
import com.leydata.backend.purposes.application.dto.PurposeResponse;
import com.leydata.backend.purposes.application.dto.UpdatePurposeRequest;
import com.leydata.backend.purposes.application.service.PurposeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/purposes")
@RequiredArgsConstructor
public class PurposeController {

    private final PurposeService purposeService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PurposeResponse create(@RequestBody @Valid CreatePurposeRequest req) {
        return purposeService.create(req);
    }

    @GetMapping
    public List<PurposeResponse> listAll() {
        return purposeService.listAll();
    }

    @GetMapping("/{id}")
    public PurposeResponse getById(@PathVariable UUID id) {
        return purposeService.getById(id);
    }

    @GetMapping("/domain/{domainId}")
    public List<PurposeResponse> listByDomain(@PathVariable UUID domainId) {
        return purposeService.listByDomain(domainId);
    }

    @PutMapping("/{id}")
    public PurposeResponse update(@PathVariable UUID id,
                                   @RequestBody @Valid UpdatePurposeRequest req) {
        return purposeService.update(id, req);
    }

    @DeleteMapping("/{id}")
    public PurposeResponse deactivate(@PathVariable UUID id) {
        return purposeService.deactivate(id);
    }
}
