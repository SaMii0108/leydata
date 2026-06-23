package com.leydata.backend.datacategory.web;

import com.leydata.backend.datacategory.application.dto.DataCategoryRequest;
import com.leydata.backend.datacategory.application.dto.DataCategoryResponse;
import com.leydata.backend.datacategory.application.service.DataCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/data-categories")
@RequiredArgsConstructor
public class DataCategoryController {

    private final DataCategoryService service;

    // GET /api/data-categories — todas las categorías activas (DPO, ADMIN, JEFE_DOMINIO)
    @GetMapping
    @PreAuthorize("hasAnyRole('DPO','ADMIN','JEFE_DOMINIO')")
    public List<DataCategoryResponse> listAll() {
        return service.listAll();
    }

    // GET /api/data-categories/sensitive — solo las categorías sensibles (Art. 16 Ley 21.719)
    @GetMapping("/sensitive")
    @PreAuthorize("hasAnyRole('DPO','ADMIN','JEFE_DOMINIO')")
    public List<DataCategoryResponse> listSensitive() {
        return service.listSensitive();
    }

    // GET /api/data-categories/{id}
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('DPO','ADMIN','JEFE_DOMINIO')")
    public DataCategoryResponse getById(@PathVariable UUID id) {
        return service.getById(id);
    }

    // POST /api/data-categories — crear categoría custom (solo DPO)
    @PostMapping
    @PreAuthorize("hasAnyRole('DPO','ADMIN')")
    public ResponseEntity<DataCategoryResponse> create(@Valid @RequestBody DataCategoryRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    // PUT /api/data-categories/{id} — editar categoría custom (solo DPO/ADMIN, no system)
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('DPO','ADMIN')")
    public DataCategoryResponse update(@PathVariable UUID id,
                                       @Valid @RequestBody DataCategoryRequest req) {
        return service.update(id, req);
    }

    // DELETE /api/data-categories/{id} — desactivar categoría custom (soft delete)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('DPO','ADMIN')")
    public DataCategoryResponse deactivate(@PathVariable UUID id) {
        return service.deactivate(id);
    }
}
