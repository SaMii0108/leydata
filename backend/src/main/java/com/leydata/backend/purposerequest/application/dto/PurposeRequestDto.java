package com.leydata.backend.purposerequest.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Solicitud de nueva finalidad de tratamiento de datos (JEFE_DOMINIO → DPO)")
public class PurposeRequestDto {

    @Schema(description = "Título descriptivo de la actividad de tratamiento propuesta",
            example = "Newsletter de ofertas y promociones")
    private String title;

    @Schema(description = "Justificación del por qué se necesita tratar estos datos",
            example = "Necesitamos comunicar descuentos y novedades a clientes suscritos voluntariamente")
    private String justification;

    @Schema(description = "Descripción de los datos personales que se tratarán",
            example = "Email, nombre completo, historial de compras")
    private String requestedData;

    @Schema(description = "UUID del dominio para el que se crea la solicitud. Debe pertenecer al jefe autenticado.",
            example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID domainId;
}
