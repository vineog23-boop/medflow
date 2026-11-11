package br.com.medflow.exam.order.application.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateExamResultRequestDto(
        @NotBlank(message = "Resultado do exame deve ser informado")
        String resultText
) {
}
