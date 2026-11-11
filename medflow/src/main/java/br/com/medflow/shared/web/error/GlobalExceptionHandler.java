package br.com.medflow.shared.web.error;

import br.com.medflow.exam.definition.application.exception.ExamDefinitionCodeConflictException;
import br.com.medflow.exam.definition.application.exception.ExamDefinitionInactiveException;
import br.com.medflow.exam.definition.application.exception.ExamDefinitionNotFoundException;
import br.com.medflow.exam.order.application.exception.ExamOrderNotFoundException;
import br.com.medflow.exam.order.application.exception.ExamOrderStatusTransitionException;
import br.com.medflow.exam.order.application.exception.ExamResultConflictException;
import br.com.medflow.exam.order.application.exception.ExamResultNotFoundException;
import br.com.medflow.patient.application.exception.PatientNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Centraliza a tradução de exceções da aplicação e do Spring MVC para
 * respostas HTTP no formato Problem Details (RFC 9457).
 *
 * <p>Com esse tratamento global, os Controllers permanecem responsáveis
 * apenas pelo fluxo HTTP normal, sem repetir blocos {@code try/catch}.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Converte a ausência de um paciente em uma resposta HTTP 404.
     */
    @ExceptionHandler(PatientNotFoundException.class)
    public ResponseEntity<ProblemDetail> handlePatientNotFound(
            PatientNotFoundException exception,
            HttpServletRequest request) {
        return createResponse(
                HttpStatus.NOT_FOUND,
                ProblemTypes.PATIENT_NOT_FOUND,
                "Paciente não encontrado",
                exception.getMessage(),
                URI.create(request.getRequestURI())
        );
    }

    /**
     * Converte a ausência de uma ordem de exame em uma resposta HTTP 404.
     */
    @ExceptionHandler(ExamOrderNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleExamOrderNotFound(
            ExamOrderNotFoundException exception,
            HttpServletRequest request) {
        return createResponse(
                HttpStatus.NOT_FOUND,
                ProblemTypes.EXAM_ORDER_NOT_FOUND,
                "Ordem de exame não encontrada",
                exception.getMessage(),
                URI.create(request.getRequestURI())
        );
    }

    @ExceptionHandler(ExamDefinitionNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleExamDefinitionNotFound(
            ExamDefinitionNotFoundException exception,
            HttpServletRequest request) {
        return createResponse(
                HttpStatus.NOT_FOUND,
                ProblemTypes.EXAM_DEFINITION_NOT_FOUND,
                "Definição de exame não encontrada",
                exception.getMessage(),
                URI.create(request.getRequestURI())
        );
    }

    @ExceptionHandler(ExamDefinitionCodeConflictException.class)
    public ResponseEntity<ProblemDetail> handleExamDefinitionCodeConflict(
            ExamDefinitionCodeConflictException exception,
            HttpServletRequest request) {
        return createResponse(
                HttpStatus.CONFLICT,
                ProblemTypes.EXAM_DEFINITION_CODE_CONFLICT,
                "Código de exame já cadastrado",
                exception.getMessage(),
                URI.create(request.getRequestURI())
        );
    }

    @ExceptionHandler(ExamDefinitionInactiveException.class)
    public ResponseEntity<ProblemDetail> handleExamDefinitionInactive(
            ExamDefinitionInactiveException exception,
            HttpServletRequest request) {
        return createResponse(
                HttpStatus.CONFLICT,
                ProblemTypes.EXAM_DEFINITION_INACTIVE,
                "Definição de exame inativa",
                exception.getMessage(),
                URI.create(request.getRequestURI())
        );
    }

    @ExceptionHandler(ExamOrderStatusTransitionException.class)
    public ResponseEntity<ProblemDetail> handleExamOrderStatusTransition(
            ExamOrderStatusTransitionException exception,
            HttpServletRequest request) {
        return createResponse(
                HttpStatus.CONFLICT,
                ProblemTypes.EXAM_ORDER_STATUS_TRANSITION_INVALID,
                "Transição de status inválida",
                exception.getMessage(),
                URI.create(request.getRequestURI())
        );
    }

    @ExceptionHandler(ExamResultNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleExamResultNotFound(
            ExamResultNotFoundException exception,
            HttpServletRequest request) {
        return createResponse(
                HttpStatus.NOT_FOUND,
                ProblemTypes.EXAM_RESULT_NOT_FOUND,
                "Resultado de exame não encontrado",
                exception.getMessage(),
                URI.create(request.getRequestURI())
        );
    }

    @ExceptionHandler(ExamResultConflictException.class)
    public ResponseEntity<ProblemDetail> handleExamResultConflict(
            ExamResultConflictException exception,
            HttpServletRequest request) {
        return createResponse(
                HttpStatus.CONFLICT,
                ProblemTypes.EXAM_RESULT_CONFLICT,
                "Conflito ao registrar resultado de exame",
                exception.getMessage(),
                URI.create(request.getRequestURI())
        );
    }

    /**
     * Trata violações das anotações de Bean Validation presentes nos DTOs.
     * O status 422 indica que o JSON foi compreendido, mas seus dados não
     * atendem ao contrato da API.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<ValidationError> errors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fieldError -> new ValidationError(
                        fieldError.getDefaultMessage() != null
                                ? fieldError.getDefaultMessage()
                                : "Valor inválido",
                        toJsonPointer(fieldError.getField())
                ))
                .sorted((first, second) -> first.pointer().compareTo(second.pointer()))
                .toList();

        ProblemDetail problem = createProblem(
                HttpStatus.UNPROCESSABLE_CONTENT,
                ProblemTypes.VALIDATION_ERROR,
                "Dados inválidos",
                "Um ou mais campos estão inválidos.",
                requestUri(request)
        );
        problem.setProperty("errors", errors);

        return createObjectResponse(problem, headers, HttpStatus.UNPROCESSABLE_CONTENT);
    }

    /**
     * Trata corpos JSON malformados ou incompatíveis com o DTO esperado.
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail problem = createProblem(
                HttpStatus.BAD_REQUEST,
                ProblemTypes.INVALID_REQUEST,
                "Requisição inválida",
                "O corpo da requisição contém JSON inválido ou incompatível.",
                requestUri(request)
        );

        return createObjectResponse(problem, headers, HttpStatus.BAD_REQUEST);
    }

    /**
     * Trata parâmetros que não podem ser convertidos para o tipo declarado
     * no Controller, como um UUID com formato inválido.
     */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail problem = createProblem(
                HttpStatus.BAD_REQUEST,
                ProblemTypes.INVALID_REQUEST,
                "Requisição inválida",
                "Um parâmetro da requisição possui formato inválido.",
                requestUri(request)
        );

        return createObjectResponse(problem, headers, HttpStatus.BAD_REQUEST);
    }

    /**
     * Impede que falhas inesperadas exponham detalhes internos ao cliente.
     * A exceção completa permanece disponível somente no log do servidor.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(
            Exception exception,
            HttpServletRequest request) {
        LOGGER.error("Erro não tratado ao processar {}", request.getRequestURI(), exception);

        return createResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ProblemTypes.INTERNAL_ERROR,
                "Erro interno",
                "Ocorreu um erro interno inesperado.",
                URI.create(request.getRequestURI())
        );
    }

    /**
     * Monta respostas dos handlers declarados diretamente nesta classe.
     */
    private ResponseEntity<ProblemDetail> createResponse(
            HttpStatus status,
            URI type,
            String title,
            String detail,
            URI instance) {
        ProblemDetail problem = createProblem(status, type, title, detail, instance);

        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    /**
     * Monta respostas dos métodos sobrescritos de
     * {@link ResponseEntityExceptionHandler}, preservando os headers recebidos
     * do Spring MVC.
     */
    private ResponseEntity<Object> createObjectResponse(
            ProblemDetail problem,
            HttpHeaders headers,
            HttpStatus status) {
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
        responseHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);

        return new ResponseEntity<>(problem, responseHeaders, status);
    }

    /**
     * Cria a estrutura comum a todos os problemas retornados pela API.
     */
    private ProblemDetail createProblem(
            HttpStatus status,
            URI type,
            String title,
            String detail,
            URI instance) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(type);
        problem.setTitle(title);
        problem.setInstance(instance);
        return problem;
    }

    /**
     * Extrai o caminho da requisição para preencher o campo {@code instance}.
     */
    private URI requestUri(WebRequest request) {
        if (request instanceof ServletWebRequest servletWebRequest) {
            return URI.create(servletWebRequest.getRequest().getRequestURI());
        }
        return URI.create("/");
    }

    /**
     * Converte o caminho de um campo Java para JSON Pointer. Por exemplo,
     * {@code address.street} torna-se {@code #/address/street}.
     */
    private String toJsonPointer(String field) {
        return Stream.of(field.split("\\."))
                .map(this::escapeJsonPointerToken)
                .collect(Collectors.joining("/", "#/", ""));
    }

    /**
     * Escapa caracteres reservados conforme a RFC 6901.
     */
    private String escapeJsonPointerToken(String token) {
        return token.replace("~", "~0").replace("/", "~1");
    }
}
