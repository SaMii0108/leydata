package com.leydata.backend.template.application.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ActivateTemplateRequest {
    /**
     * true si los cambios son sustanciales (Ley 21.719) y todos los titulares
     * con acuerdos activos en versiones anteriores deben re-consentir.
     */
    private Boolean forceReconsent = false;
}
