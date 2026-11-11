package br.com.medflow.exam.order.application.dto.request;

import br.com.medflow.exam.order.domain.enums.ExamStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateExamStatusRequestDto(
        @NotNull(message = "Status do exame deve ser informado")
        ExamStatus status
) {
}
