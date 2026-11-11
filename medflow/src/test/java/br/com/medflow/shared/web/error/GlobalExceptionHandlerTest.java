package br.com.medflow.shared.web.error;

import br.com.medflow.exam.definition.application.exception.ExamDefinitionCodeConflictException;
import br.com.medflow.exam.definition.application.exception.ExamDefinitionInactiveException;
import br.com.medflow.exam.definition.application.exception.ExamDefinitionNotFoundException;
import br.com.medflow.exam.order.application.exception.ExamOrderNotFoundException;
import br.com.medflow.exam.order.application.exception.ExamOrderStatusTransitionException;
import br.com.medflow.exam.order.application.exception.ExamResultConflictException;
import br.com.medflow.exam.order.application.exception.ExamResultNotFoundException;
import br.com.medflow.exam.order.domain.enums.ExamStatus;
import br.com.medflow.patient.application.dto.PatientDtoRequest;
import br.com.medflow.patient.application.exception.PatientNotFoundException;
import jakarta.validation.Valid;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class GlobalExceptionHandlerTest {

    private static final String ID = "76cb605b-71dc-47e8-83b5-83f2ef99a7cf";

    private LocalValidatorFactoryBean validator;
    private MockMvc mockMvc;

    @BeforeEach
    void configurarMockMvc() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = standaloneSetup(new ErrorProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void fecharValidator() {
        validator.close();
    }

    @Test
    void deveRetornarProblemDetailQuandoPacienteNaoExistir() throws Exception {
        mockMvc.perform(get("/test-errors/patients/{id}", ID))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type")
                        .value("urn:medflow:problem:patient-not-found"))
                .andExpect(jsonPath("$.title").value("Paciente não encontrado"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value(
                        "Paciente com id: " + ID + " não encontrado"))
                .andExpect(jsonPath("$.instance").value(
                        "/test-errors/patients/" + ID));
    }

    @Test
    void deveRetornarProblemDetailQuandoOrdemDeExameNaoExistir() throws Exception {
        mockMvc.perform(get("/test-errors/exam-orders/{id}", ID))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type")
                        .value("urn:medflow:problem:exam-order-not-found"))
                .andExpect(jsonPath("$.title").value("Ordem de exame não encontrada"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value(
                        "Ordem de exame não encontrada: " + ID))
                .andExpect(jsonPath("$.instance").value(
                        "/test-errors/exam-orders/" + ID));
    }

    @Test
    void deveRetornarProblemDetailQuandoDefinicaoDeExameNaoExistir() throws Exception {
        mockMvc.perform(get("/test-errors/exam-definitions/missing/{code}", "HEMOGRAMA"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type")
                        .value("urn:medflow:problem:exam-definition-not-found"))
                .andExpect(jsonPath("$.title").value("Definição de exame não encontrada"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value(
                        "Definição de exame não encontrada: HEMOGRAMA"))
                .andExpect(jsonPath("$.instance").value(
                        "/test-errors/exam-definitions/missing/HEMOGRAMA"));
    }

    @Test
    void deveRetornarProblemDetailQuandoCodigoDeExameJaEstiverCadastrado() throws Exception {
        mockMvc.perform(get("/test-errors/exam-definitions/conflict/{code}", "HEMOGRAMA"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type")
                        .value("urn:medflow:problem:exam-definition-code-conflict"))
                .andExpect(jsonPath("$.title").value("Código de exame já cadastrado"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value(
                        "Já existe uma definição de exame com o código: HEMOGRAMA"))
                .andExpect(jsonPath("$.instance").value(
                        "/test-errors/exam-definitions/conflict/HEMOGRAMA"));
    }

    @Test
    void deveRetornarProblemDetailQuandoDefinicaoDeExameEstiverInativa() throws Exception {
        mockMvc.perform(get("/test-errors/exam-definitions/inactive/{code}", "HEMOGRAMA"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type")
                        .value("urn:medflow:problem:exam-definition-inactive"))
                .andExpect(jsonPath("$.title").value("Definição de exame inativa"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value(
                        "Definição de exame inativa: HEMOGRAMA"))
                .andExpect(jsonPath("$.instance").value(
                        "/test-errors/exam-definitions/inactive/HEMOGRAMA"));
    }

    @Test
    void deveRetornarProblemDetailQuandoTransicaoDeStatusForInvalida() throws Exception {
        mockMvc.perform(get("/test-errors/exam-orders/{id}/status-conflict", ID))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type")
                        .value("urn:medflow:problem:exam-order-status-transition-invalid"))
                .andExpect(jsonPath("$.title").value("Transição de status inválida"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.instance").value(
                        "/test-errors/exam-orders/" + ID + "/status-conflict"));
    }

    @Test
    void deveRetornarProblemDetailQuandoResultadoDeExameNaoExistir() throws Exception {
        mockMvc.perform(get("/test-errors/exam-orders/{id}/result-missing", ID))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type")
                        .value("urn:medflow:problem:exam-result-not-found"))
                .andExpect(jsonPath("$.title").value("Resultado de exame não encontrado"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.instance").value(
                        "/test-errors/exam-orders/" + ID + "/result-missing"));
    }

    @Test
    void deveRetornarProblemDetailQuandoResultadoDeExameEstiverEmConflito() throws Exception {
        mockMvc.perform(get("/test-errors/exam-orders/{id}/result-conflict", ID))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type")
                        .value("urn:medflow:problem:exam-result-conflict"))
                .andExpect(jsonPath("$.title").value("Conflito ao registrar resultado de exame"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.instance").value(
                        "/test-errors/exam-orders/" + ID + "/result-conflict"));
    }

    @Test
    void deveRetornarErrosDeValidacaoComJsonPointers() throws Exception {
        String body = """
                {
                  "fullName": "",
                  "cpf": "123",
                  "birthDate": "2999-01-01"
                }
                """;

        mockMvc.perform(post("/test-errors/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type")
                        .value("urn:medflow:problem:validation-error"))
                .andExpect(jsonPath("$.title").value("Dados inválidos"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail")
                        .value("Um ou mais campos estão inválidos."))
                .andExpect(jsonPath("$.instance").value("/test-errors/validation"))
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors[0].detail")
                        .value("Data de nascimento não pode estar no futuro"))
                .andExpect(jsonPath("$.errors[0].pointer").value("#/birthDate"))
                .andExpect(jsonPath("$.errors[1].detail").value("CPF deve ser válido"))
                .andExpect(jsonPath("$.errors[1].pointer").value("#/cpf"))
                .andExpect(jsonPath("$.errors[2].detail")
                        .value("Nome completo deve ser informado"))
                .andExpect(jsonPath("$.errors[2].pointer").value("#/fullName"));
    }

    @Test
    void deveRetornarMensagensParaDemaisRestricoesDePaciente() throws Exception {
        String body = """
                {
                  "fullName": "%s"
                }
                """.formatted("A".repeat(151));

        mockMvc.perform(post("/test-errors/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors[0].detail")
                        .value("Data de nascimento deve ser informada"))
                .andExpect(jsonPath("$.errors[0].pointer").value("#/birthDate"))
                .andExpect(jsonPath("$.errors[1].detail").value("CPF deve ser informado"))
                .andExpect(jsonPath("$.errors[1].pointer").value("#/cpf"))
                .andExpect(jsonPath("$.errors[2].detail")
                        .value("Nome completo deve ter no máximo 150 caracteres"))
                .andExpect(jsonPath("$.errors[2].pointer").value("#/fullName"));
    }

    @Test
    void deveRetornarRequisicaoInvalidaQuandoJsonEstiverMalformado() throws Exception {
        mockMvc.perform(post("/test-errors/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type")
                        .value("urn:medflow:problem:invalid-request"))
                .andExpect(jsonPath("$.title").value("Requisição inválida"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value(
                        "O corpo da requisição contém JSON inválido ou incompatível."))
                .andExpect(jsonPath("$.instance").value("/test-errors/body"));
    }

    @Test
    void deveRetornarRequisicaoInvalidaQuandoTipoDoParametroForInvalido() throws Exception {
        mockMvc.perform(get("/test-errors/uuid/uuid-invalido"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type")
                        .value("urn:medflow:problem:invalid-request"))
                .andExpect(jsonPath("$.title").value("Requisição inválida"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value(
                        "Um parâmetro da requisição possui formato inválido."))
                .andExpect(jsonPath("$.instance")
                        .value("/test-errors/uuid/uuid-invalido"));
    }

    @Test
    void deveRetornarProblemDetailSeguroParaExcecaoInesperada() throws Exception {
        mockMvc.perform(get("/test-errors/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type")
                        .value("urn:medflow:problem:internal-error"))
                .andExpect(jsonPath("$.title").value("Erro interno"))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail")
                        .value("Ocorreu um erro interno inesperado."))
                .andExpect(jsonPath("$.instance").value("/test-errors/unexpected"))
                .andExpect(content().string(not(containsString(
                        "segredo-interno-do-servidor"))));
    }

    @RestController
    @RequestMapping("/test-errors")
    static class ErrorProbeController {

        @GetMapping("/patients/{id}")
        void patientNotFound(@PathVariable UUID id) {
            throw new PatientNotFoundException(id);
        }

        @GetMapping("/exam-orders/{id}")
        void examOrderNotFound(@PathVariable UUID id) {
            throw new ExamOrderNotFoundException(id);
        }

        @GetMapping("/exam-definitions/missing/{code}")
        void examDefinitionNotFound(@PathVariable String code) {
            throw new ExamDefinitionNotFoundException(code);
        }

        @GetMapping("/exam-definitions/conflict/{code}")
        void examDefinitionCodeConflict(@PathVariable String code) {
            throw new ExamDefinitionCodeConflictException(code);
        }

        @GetMapping("/exam-definitions/inactive/{code}")
        void examDefinitionInactive(@PathVariable String code) {
            throw new ExamDefinitionInactiveException(code);
        }

        @GetMapping("/exam-orders/{id}/status-conflict")
        void examOrderStatusConflict(@PathVariable UUID id) {
            throw new ExamOrderStatusTransitionException(id, ExamStatus.AWAITING_COLLECTION, ExamStatus.RELEASED);
        }

        @GetMapping("/exam-orders/{id}/result-missing")
        void examResultMissing(@PathVariable UUID id) {
            throw new ExamResultNotFoundException(id);
        }

        @GetMapping("/exam-orders/{id}/result-conflict")
        void examResultConflict(@PathVariable UUID id) {
            throw new ExamResultConflictException(id, "laudo já cadastrado");
        }

        @PostMapping("/validation")
        void validation(@Valid @RequestBody PatientDtoRequest request) {
        }

        @PostMapping("/body")
        void body(@RequestBody PatientDtoRequest request) {
        }

        @GetMapping("/uuid/{id}")
        void uuid(@PathVariable UUID id) {
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("segredo-interno-do-servidor");
        }
    }
}
