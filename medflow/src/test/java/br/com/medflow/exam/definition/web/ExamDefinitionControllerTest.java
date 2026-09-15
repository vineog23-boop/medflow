package br.com.medflow.exam.definition.web;

import br.com.medflow.exam.definition.application.ExamDefinitionService;
import br.com.medflow.exam.definition.application.dto.request.CreateExamDefinitionRequestDto;
import br.com.medflow.exam.definition.application.dto.request.UpdateExamDefinitionRequestDto;
import br.com.medflow.exam.definition.application.dto.response.ExamDefinitionResponseDto;
import br.com.medflow.exam.definition.domain.enums.SampleType;
import br.com.medflow.shared.web.error.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * Protege o contrato HTTP do catálogo de exames sem inicializar banco ou infraestrutura.
 */
class ExamDefinitionControllerTest {

    private static final UUID ID = UUID.fromString(
            "76cb605b-71dc-47e8-83b5-83f2ef99a7cf"
    );

    private final ExamDefinitionService examDefinitionService = mock(ExamDefinitionService.class);

    private LocalValidatorFactoryBean validator;
    private MockMvc mockMvc;

    @BeforeEach
    void configurarMockMvc() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = standaloneSetup(new ExamDefinitionController(examDefinitionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void fecharValidator() {
        validator.close();
    }

    @Test
    void deveBuscarDefinicaoPorId() throws Exception {
        when(examDefinitionService.findById(ID)).thenReturn(response());

        mockMvc.perform(get("/exam-definitions/{id}", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.code").value("HEMOGRAMA"));

        verify(examDefinitionService).findById(ID);
    }

    @Test
    void deveListarDefinicoesPaginadas() throws Exception {
        when(examDefinitionService.findAll(any())).thenReturn(new PageImpl<>(
                List.of(response()),
                PageRequest.of(0, 10),
                1
        ));

        mockMvc.perform(get("/exam-definitions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(ID.toString()))
                .andExpect(jsonPath("$.content[0].sampleType").value("BLOOD"));
    }

    @Test
    void deveCriarDefinicao() throws Exception {
        CreateExamDefinitionRequestDto request = createRequest();
        when(examDefinitionService.create(request)).thenReturn(response());

        mockMvc.perform(post("/exam-definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "HEMOGRAMA",
                                  "name": "Hemograma completo",
                                  "sampleType": "BLOOD"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.active").value(true));

        verify(examDefinitionService).create(request);
    }

    @Test
    void deveAtualizarDefinicao() throws Exception {
        UpdateExamDefinitionRequestDto request = updateRequest();
        when(examDefinitionService.update(eq(ID), eq(request))).thenReturn(response());

        mockMvc.perform(put("/exam-definitions/{id}", ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Hemograma completo",
                                  "sampleType": "BLOOD"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.name").value("Hemograma completo"));

        verify(examDefinitionService).update(ID, request);
    }

    @Test
    void deveDesativarDefinicao() throws Exception {
        mockMvc.perform(patch("/exam-definitions/{id}/deactivation", ID))
                .andExpect(status().isNoContent());

        verify(examDefinitionService).deactivate(ID);
    }

    @Test
    void naoDeveCriarDefinicaoComDadosInvalidos() throws Exception {
        mockMvc.perform(post("/exam-definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "",
                                  "name": "",
                                  "sampleType": null
                                }
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(
                        "urn:medflow:problem:validation-error"));

        verifyNoInteractions(examDefinitionService);
    }

    private CreateExamDefinitionRequestDto createRequest() {
        return new CreateExamDefinitionRequestDto(
                "HEMOGRAMA",
                "Hemograma completo",
                SampleType.BLOOD
        );
    }

    private UpdateExamDefinitionRequestDto updateRequest() {
        return new UpdateExamDefinitionRequestDto(
                "Hemograma completo",
                SampleType.BLOOD
        );
    }

    private ExamDefinitionResponseDto response() {
        return new ExamDefinitionResponseDto(
                ID,
                "HEMOGRAMA",
                "Hemograma completo",
                SampleType.BLOOD,
                true,
                Instant.parse("2026-09-12T12:00:00Z"),
                Instant.parse("2026-09-12T12:00:00Z")
        );
    }
}
