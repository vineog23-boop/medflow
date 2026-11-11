package br.com.medflow.exam.order.application.exception;

import br.com.medflow.exam.order.domain.enums.ExamStatus;

import java.util.UUID;

/**
 * Indica tentativa inválida de transição de status em uma ordem de exame.
 */
public class ExamOrderStatusTransitionException extends RuntimeException {

    public ExamOrderStatusTransitionException(UUID orderId, ExamStatus currentStatus, ExamStatus targetStatus) {
        super("Não é possível alterar a ordem " + orderId + " do status " + currentStatus + " para " + targetStatus);
    }
}
