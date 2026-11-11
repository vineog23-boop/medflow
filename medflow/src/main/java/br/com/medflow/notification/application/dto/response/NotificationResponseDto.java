package br.com.medflow.notification.application.dto.response;

import br.com.medflow.exam.order.domain.enums.ExamStatus;
import br.com.medflow.notification.domain.Notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponseDto(
        UUID id,
        UUID orderId,
        UUID patientId,
        String examCode,
        ExamStatus status,
        String message,
        Instant createdAt
) {

    public NotificationResponseDto(Notification notification) {
        this(
                notification.getId(),
                notification.getOrderId(),
                notification.getPatientId(),
                notification.getExamCode(),
                notification.getStatus(),
                notification.getMessage(),
                notification.getCreatedAt()
        );
    }
}
