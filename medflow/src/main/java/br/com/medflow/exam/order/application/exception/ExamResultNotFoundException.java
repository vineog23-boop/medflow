package br.com.medflow.exam.order.application.exception;

import java.util.UUID;

/**
 * Indica que uma ordem de exame ainda não possui resultado clínico liberado.
 */
public class ExamResultNotFoundException extends RuntimeException {

    public ExamResultNotFoundException(UUID orderId) {
        super("Resultado não encontrado para a ordem de exame: " + orderId);
    }
}
