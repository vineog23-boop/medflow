package br.com.medflow.exam.order.domain.event;

import br.com.medflow.exam.order.domain.ExamOrder;
import br.com.medflow.exam.order.domain.enums.ExamStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento emitido pelo laboratório quando um exame evolui de etapa ou tem seu resultado liberado.
 * Trafega pelo Kafka (tópico exam-notifications.v1) para gerar notificações ao paciente.
 */
public record ExamLaboratoryUpdatedEvent(
        UUID eventId,
        UUID orderId,
        UUID patientId,
        String examCode,
        ExamStatus status,
        Instant occurredAt
) {

    public ExamLaboratoryUpdatedEvent(ExamOrder examOrder) {
        this(
                UUID.randomUUID(),
                examOrder.getId(),
                examOrder.getPatientId(),
                examOrder.getExamCode(),
                examOrder.getStatus(),
                Instant.now()
        );
    }

    public ExamLaboratoryUpdatedEvent(UUID orderId, UUID patientId, String examCode, ExamStatus status) {
        this(
                UUID.randomUUID(),
                orderId,
                patientId,
                examCode,
                status,
                Instant.now()
        );
    }
}
