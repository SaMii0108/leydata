package com.leydata.backend.template.web;

import com.leydata.backend.config.GlobalExceptionHandler;
import com.leydata.backend.privacydoc.domain.exception.BusinessValidationException;
import com.leydata.backend.template.application.dto.AddTemplatePurposeRequest;
import com.leydata.backend.template.application.dto.CreateTemplateRequest;
import com.leydata.backend.template.application.dto.TemplatePurposeResponse;
import com.leydata.backend.template.application.dto.TemplateResponse;
import com.leydata.backend.template.application.dto.UpdateTemplatePurposeRequest;
import com.leydata.backend.template.application.service.TemplateService;
import com.leydata.backend.template.domain.exception.TemplateNotFoundException;
import com.leydata.backend.user.infrastructure.persistence.UsersRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TemplateController.class)
@org.springframework.context.annotation.Import(GlobalExceptionHandler.class)
@WithMockUser(roles = "DPO")
class TemplateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TemplateService service;

    // Requerido por UserStatusFilter (filtro de seguridad ejecutado en cada request)
    @MockitoBean
    private UsersRepository usersRepository;

    private TemplateResponse draftResponse(UUID id) {
        return TemplateResponse.builder()
                .id(id)
                .templateKey("CONSENT_X")
                .version(1)
                .name("Consentimiento X")
                .isActive(false)
                .status("DRAFT")
                .build();
    }

    @Test
    void create_devuelve201ConTemplateCreado() throws Exception {
        CreateTemplateRequest req = new CreateTemplateRequest();
        req.setTemplateKey("consent_x");
        req.setName("Consentimiento X");

        UUID id = UUID.randomUUID();
        when(service.create(any())).thenReturn(draftResponse(id));

        mockMvc.perform(post("/api/templates")
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.templateKey").value("CONSENT_X"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void create_devuelve400_siFaltaNameObligatorio() throws Exception {
        CreateTemplateRequest req = new CreateTemplateRequest();
        req.setTemplateKey("consent_x");
        // name vacío -> @NotBlank

        mockMvc.perform(post("/api/templates")
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getById_devuelve200ConTemplate() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.getById(id)).thenReturn(draftResponse(id));

        mockMvc.perform(get("/api/templates/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void getById_devuelve500_porFaltaDeHandlerParaTemplateNotFoundException() throws Exception {
        // NOTA: TemplateNotFoundException no está registrada en GlobalExceptionHandler,
        // por lo que cae en el handler genérico de RuntimeException y responde 500 en vez de 404.
        UUID id = UUID.randomUUID();
        when(service.getById(id)).thenThrow(new TemplateNotFoundException(id));

        mockMvc.perform(get("/api/templates/{id}", id))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void list_devuelve200ConListaDeTemplates() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.list(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(draftResponse(id)));

        mockMvc.perform(get("/api/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()));
    }

    @Test
    void getHistory_devuelve200ConVersionesOrdenadas() throws Exception {
        when(service.getHistory("CONSENT_X")).thenReturn(List.of(draftResponse(UUID.randomUUID())));

        mockMvc.perform(get("/api/templates/family/{templateKey}", "CONSENT_X"))
                .andExpect(status().isOk());
    }

    @Test
    void getActive_devuelve200ConVersionActiva() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.getActive("CONSENT_X")).thenReturn(draftResponse(id));

        mockMvc.perform(get("/api/templates/active/{templateKey}", "CONSENT_X"))
                .andExpect(status().isOk());
    }

    @Test
    void getActive_devuelve422_siNoHayVersionActiva() throws Exception {
        when(service.getActive("CONSENT_X"))
                .thenThrow(new BusinessValidationException("No hay una versión activa para el template CONSENT_X"));

        mockMvc.perform(get("/api/templates/active/{templateKey}", "CONSENT_X"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void approve_devuelve200ConTemplateAprobado() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.approve(id)).thenReturn(draftResponse(id));

        mockMvc.perform(post("/api/templates/{id}/approve", id).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void approve_devuelve422_sinPurposeVisible() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.approve(id)).thenThrow(new BusinessValidationException(
                "El template debe tener al menos una finalidad visible antes de aprobarse"));

        mockMvc.perform(post("/api/templates/{id}/approve", id).with(csrf()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void activate_devuelve200ConTemplateActivado() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.activate(id)).thenReturn(draftResponse(id));

        mockMvc.perform(post("/api/templates/{id}/activate", id).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void activate_devuelve422_siTemplateNoAprobado() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.activate(id)).thenThrow(new BusinessValidationException(
                "El template debe estar aprobado antes de activarse"));

        mockMvc.perform(post("/api/templates/{id}/activate", id).with(csrf()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void addPurpose_devuelve204_alVincularPurpose() throws Exception {
        UUID id = UUID.randomUUID();
        AddTemplatePurposeRequest req = new AddTemplatePurposeRequest();
        req.setPurposeId(UUID.randomUUID());
        req.setOrderPosition(1);

        mockMvc.perform(post("/api/templates/{id}/purposes", id)
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        verify(service).addPurpose(eq(id), any());
    }

    @Test
    void addPurpose_devuelve422_siOrderPositionDuplicado() throws Exception {
        UUID id = UUID.randomUUID();
        AddTemplatePurposeRequest req = new AddTemplatePurposeRequest();
        req.setPurposeId(UUID.randomUUID());
        req.setOrderPosition(1);

        org.mockito.Mockito.doThrow(new BusinessValidationException("Ya existe una finalidad en la posición 1"))
                .when(service).addPurpose(eq(id), any());

        mockMvc.perform(post("/api/templates/{id}/purposes", id)
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void removePurpose_devuelve204_alDesvincularPurpose() throws Exception {
        UUID id = UUID.randomUUID();
        UUID purposeId = UUID.randomUUID();

        mockMvc.perform(delete("/api/templates/{id}/purposes/{purposeId}", id, purposeId).with(csrf()))
                .andExpect(status().isNoContent());

        verify(service).removePurpose(id, purposeId);
    }

    @Test
    void listPurposes_devuelve200ConListaOrdenada() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.listPurposes(id)).thenReturn(List.of(
                TemplatePurposeResponse.builder().purposeId(UUID.randomUUID()).orderPosition(1).isVisible(true).build()));

        mockMvc.perform(get("/api/templates/{id}/purposes", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderPosition").value(1));
    }

    @Test
    void updatePurpose_devuelve200ConValoresActualizados() throws Exception {
        UUID id = UUID.randomUUID();
        UUID purposeId = UUID.randomUUID();
        UpdateTemplatePurposeRequest req = new UpdateTemplatePurposeRequest();
        req.setOrderPosition(2);
        req.setIsVisible(false);

        when(service.updatePurpose(eq(id), eq(purposeId), any())).thenReturn(
                TemplatePurposeResponse.builder().purposeId(purposeId).orderPosition(2).isVisible(false).build());

        mockMvc.perform(patch("/api/templates/{id}/purposes/{purposeId}", id, purposeId)
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderPosition").value(2))
                .andExpect(jsonPath("$.isVisible").value(false));
    }
}
