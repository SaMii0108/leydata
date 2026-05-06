package com.leydata.backend.service;

import com.leydata.backend.dto.PurposeRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PurposeRequestService {

    @Transactional
    public Object createPurposeRequest(PurposeRequestDto request, UUID requesterId) {
        // Template: Aquí va la lógica para crear solicitud de propósito
        // Usa repositorios según tu modelo de datos
        throw new UnsupportedOperationException("Implementar según modelo específico");
    }
}
