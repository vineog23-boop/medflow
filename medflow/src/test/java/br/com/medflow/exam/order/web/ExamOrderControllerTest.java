package br.com.medflow.exam.order.web;

import br.com.medflow.exam.order.application.ExamOrderService;
import br.com.medflow.exam.order.application.dto.request.CreateExamResultRequestDto;
import br.com.medflow.exam.order.application.dto.request.UpdateExamStatusRequestDto;
import br.com.medflow.exam.order.application.dto.response.ExamOrderResponseDto;
import br.com.medflow.exam.order.application.dto.response.ExamResultResponseDto;
import br.com.medflow.exam.order.application.exception.ExamOrderNotFoundException;
import br.com.medflow.exam.order.application.exception.ExamOrderStatusTransitionException;
import br.com.medflow.exam.order.application.exception.ExamResultConflictException;
import br.com.medflow.exam.order.application.exception.ExamResultNotFoundException;
import br.com.medflow.exam.order.domain.enums.ExamPriority;
import br.com.medflow.exam.order.domain.enums.ExamStatus;
import br.com.medflow.shared.web.error.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ExamOrderControllerTest {

    private static final UUID ORDER_ID = UUID.fromString("76cb605b-71dc-47e8-83b5-83f2ef99a7cf");
    private static final UUID PATIENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final ExamOrderService examOrderService = mock(ExamOrderService.class);

    private LocalValidatorFactoryBean validator;
    private MockMvc mockMvc;

    @BeforeEach
    void configurarMockMvc() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = standaloneSetup(new ExamOrderController(examOrderService))
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
    void deveAtualizarStatusDaOrdemNoLaboratorio() throws Exception {
        UpdateExamStatusRequestDto request = new UpdateExamStatusRequestDto(ExamStatus.COLLECTED);
        ExamOrderResponseDto response = new ExamOrderResponseDto(
                ORDER_ID,
                PATIENT_ID,
                "HEMOGRAMA",
                ExamPriority.URGENT,
                ExamStatus.COLLECTED,
                Instant.parse("2026-10-12T10:00:00Z"),
                Instant.parse("2026-10-12T10:05:00Z")
        );
        when(examOrderService.updateStatus(ORDER_ID, request)).thenReturn(response);

        mockMvc.perform(patch("/exam-orders/{id}/status", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "COLLECTED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.status").value("COLLECTED"));

        verify(examOrderService).updateStatus(ORDER_ID, request);
    }

    @Test
    void naoDeveAtualizarStatusQuandoStatusForNulo() throws Exception {
        mockMvc.perform(patch("/exam-orders/{id}/status", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": null
                                }
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("urn:medflow:problem:validation-error"))
                .andExpect(jsonPath("$.errors[0].pointer").value("#/status"));

        verifyNoInteractions(examOrderService);
    }

    @Test
    void deveRegistrarResultadoDoExame() throws Exception {
        CreateExamResultRequestDto request = new CreateExamResultRequestDto("Sem alterações clínicas.");
        ExamResultResponseDto response = new ExamResultResponseDto(
                UUID.randomUUID(),
                ORDER_ID,
                PATIENT_ID,
                "HEMOGRAMA",
                "Sem alterações clínicas.",
                Instant.parse("2026-10-12T12:00:00Z")
        );
        when(examOrderService.registerResult(ORDER_ID, request)).thenReturn(response);

        mockMvc.perform(post("/exam-orders/{id}/result", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resultText": "Sem alterações clínicas."
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.resultText").value("Sem alterações clínicas."));

        verify(examOrderService).registerResult(ORDER_ID, request);
    }

    @Test
    void deveConsultarResultadoDoExamePorOrdemId() throws Exception {
        ExamResultResponseDto response = new ExamResultResponseDto(
                UUID.randomUUID(),
                ORDER_ID,
                PATIENT_ID,
                "HEMOGRAMA",
                "Hemograma dentro da normalidade.",
                Instant.parse("2026-10-12T12:00:00Z")
        );
        when(examOrderService.findResultByOrderId(ORDER_ID)).thenReturn(response);

        mockMvc.perform(get("/exam-orders/{id}/result", ORDER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.resultText").value("Hemograma dentro da normalidade."));

        verify(examOrderService).findResultByOrderId(ORDER_ID);
    }

    @Test
    void deveRetornar404QuandoAtualizarStatusDeOrdemInexistente() throws Exception {
        UpdateExamStatusRequestDto request = new UpdateExamStatusRequestDto(ExamStatus.COLLECTED);
        when(examOrderService.updateStatus(ORDER_ID, request))
                .thenThrow(new ExamOrderNotFoundException(ORDER_ID));

        mockMvc.perform(patch("/exam-orders/{id}/status", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "COLLECTED"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:medflow:problem:exam-order-not-found"));
    }

    @Test
    void deveRetornar409QuandoTransicaoDeStatusForInvalida() throws Exception {
        UpdateExamStatusRequestDto request = new UpdateExamStatusRequestDto(ExamStatus.RELEASED);
        when(examOrderService.updateStatus(ORDER_ID, request))
                .thenThrow(new ExamOrderStatusTransitionException(
                        ORDER_ID, ExamStatus.AWAITING_COLLECTION, ExamStatus.RELEASED));

        mockMvc.perform(patch("/exam-orders/{id}/status", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "RELEASED"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:medflow:problem:exam-order-status-transition-invalid"));
    }

    @Test
    void deveRetornar409QuandoResultadoEstiverEmConflito() throws Exception {
        CreateExamResultRequestDto request = new CreateExamResultRequestDto("Laudo repetido");
        when(examOrderService.registerResult(ORDER_ID, request))
                .thenThrow(new ExamResultConflictException(ORDER_ID, "a ordem já possui resultado liberado"));

        mockMvc.perform(post("/exam-orders/{id}/result", ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resultText": "Laudo repetido"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:medflow:problem:exam-result-conflict"));
    }

    @Test
    void deveRetornar404QuandoResultadoNaoExistir() throws Exception {
        when(examOrderService.findResultByOrderId(ORDER_ID))
                .thenThrow(new ExamResultNotFoundException(ORDER_ID));

        mockMvc.perform(get("/exam-orders/{id}/result", ORDER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:medflow:problem:exam-result-not-found"));
    }
}
