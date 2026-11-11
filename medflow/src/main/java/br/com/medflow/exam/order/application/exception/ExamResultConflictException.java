package br.com.medflow.exam.order.application.exception;

import java.util.UUID;

/**
 * Indica conflito ao tentar registrar resultado para ordem já liberada, cancelada ou rejeitada.
 */
public class ExamResultConflictException extends RuntimeException {

    public ExamResultConflictException(UUID orderId, String reason) {
        super("Não é possível registrar resultado para a ordem " + orderId + ": " + reason);
    }
}
