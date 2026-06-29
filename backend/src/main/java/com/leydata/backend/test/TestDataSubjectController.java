package com.leydata.backend.test;

import com.leydata.backend.entity.DataSubjects;
import com.leydata.backend.repository.DataSubjectsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * ⚠️ PROVISORIO — NO MERGEAR A MAIN. ⚠️
 *
 * Creado específicamente para sembrar un DATA_SUBJECT de prueba y poder ejecutar
 * el flujo end-to-end de Agreements en local (ver docs/agreements-manual-test-flow.md).
 * DATA_SUBJECTS es responsabilidad del orquestador en producción; sin este endpoint
 * no hay forma de crear una fila de prueba mientras el orquestador no existe.
 *
 * Eliminar este archivo una vez que el orquestador (o un mecanismo equivalente)
 * pueda poblar DATA_SUBJECTS, o antes de mergear esta rama a main.
 */
@RestController
@RequestMapping("/api/test/data-subjects")
@RequiredArgsConstructor
public class TestDataSubjectController {

    private final DataSubjectsRepository repository;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@RequestBody Map<String, String> body) {
        DataSubjects subject = new DataSubjects();
        subject.setIdentifier(body.get("identifier"));
        subject.setCreatedAt(LocalDateTime.now());
        DataSubjects saved = repository.save(subject);
        return Map.of("id", saved.getId(), "identifier", saved.getIdentifier());
    }
}
