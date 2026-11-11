package br.com.medflow.exam.order.application.dto.response;

import br.com.medflow.exam.order.domain.ExamResult;

import java.time.Instant;
import java.util.UUID;

public record ExamResultResponseDto(
        UUID id,
        UUID orderId,
        UUID patientId,
        String examCode,
        String resultText,
        Instant releasedAt
) {

    public ExamResultResponseDto(ExamResult result) {
        this(
                result.getId(),
                result.getOrderId(),
                result.getPatientId(),
                result.getExamCode(),
                result.getResultText(),
                result.getReleasedAt()
        );
    }
}
