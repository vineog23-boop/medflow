package br.com.medflow.shared.web.error;

import java.net.URI;

/**
 * Catálogo central dos identificadores de problemas expostos pela API.
 *
 * <p>As URNs funcionam como identificadores estáveis do contrato HTTP e não
 * dependem da existência de um domínio público.</p>
 */
public final class ProblemTypes {

    public static final URI PATIENT_NOT_FOUND =
            URI.create("urn:medflow:problem:patient-not-found");
    public static final URI EXAM_ORDER_NOT_FOUND =
            URI.create("urn:medflow:problem:exam-order-not-found");
    public static final URI EXAM_DEFINITION_NOT_FOUND =
            URI.create("urn:medflow:problem:exam-definition-not-found");
    public static final URI EXAM_DEFINITION_CODE_CONFLICT =
            URI.create("urn:medflow:problem:exam-definition-code-conflict");
    public static final URI EXAM_DEFINITION_INACTIVE =
            URI.create("urn:medflow:problem:exam-definition-inactive");
    public static final URI EXAM_ORDER_STATUS_TRANSITION_INVALID =
            URI.create("urn:medflow:problem:exam-order-status-transition-invalid");
    public static final URI EXAM_RESULT_NOT_FOUND =
            URI.create("urn:medflow:problem:exam-result-not-found");
    public static final URI EXAM_RESULT_CONFLICT =
            URI.create("urn:medflow:problem:exam-result-conflict");
    public static final URI INVALID_REQUEST =
            URI.create("urn:medflow:problem:invalid-request");
    public static final URI VALIDATION_ERROR =
            URI.create("urn:medflow:problem:validation-error");
    public static final URI INTERNAL_ERROR =
            URI.create("urn:medflow:problem:internal-error");

    private ProblemTypes() {
    }
}
