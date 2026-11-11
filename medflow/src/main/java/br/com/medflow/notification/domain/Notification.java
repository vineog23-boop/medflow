package br.com.medflow.notification.domain;

import br.com.medflow.exam.order.domain.enums.ExamStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * Representa uma notificação gerada de forma assíncrona via Kafka para consulta do paciente ou recepção.
 */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @NotNull
    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @NotNull
    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @NotBlank
    @Size(max = 50)
    @Column(name = "exam_code", nullable = false, length = 50)
    private String examCode;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ExamStatus status;

    @NotBlank
    @Size(max = 500)
    @Column(name = "message", nullable = false, length = 500)
    private String message;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Notification() {
    }

    public Notification(
            UUID eventId,
            UUID orderId,
            UUID patientId,
            String examCode,
            ExamStatus status,
            String message) {
        validateData(eventId, orderId, patientId, examCode, status, message);

        this.eventId = eventId;
        this.orderId = orderId;
        this.patientId = patientId;
        this.examCode = examCode.trim();
        this.status = status;
        this.message = message.trim();
        this.createdAt = Instant.now();
    }

    private static void validateData(
            UUID eventId,
            UUID orderId,
            UUID patientId,
            String examCode,
            ExamStatus status,
            String message) {
        if (eventId == null) {
            throw new IllegalArgumentException("Evento deve ser informado");
        }
        if (orderId == null) {
            throw new IllegalArgumentException("Ordem de exame deve ser informada");
        }
        if (patientId == null) {
            throw new IllegalArgumentException("Paciente deve ser informado");
        }
        if (examCode == null || examCode.isBlank()) {
            throw new IllegalArgumentException("Código do exame deve ser informado");
        }
        if (examCode.trim().length() > 50) {
            throw new IllegalArgumentException("Código do exame deve ter no máximo 50 caracteres");
        }
        if (status == null) {
            throw new IllegalArgumentException("Status do exame deve ser informado");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Mensagem da notificação deve ser informada");
        }
        if (message.trim().length() > 500) {
            throw new IllegalArgumentException("Mensagem da notificação deve ter no máximo 500 caracteres");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public String getExamCode() {
        return examCode;
    }

    public ExamStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
