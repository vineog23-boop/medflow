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

import java.time.Instant;
import java.util.Objects;
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

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "exam_code", nullable = false, length = 50)
    private String examCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ExamStatus status;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

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
        this.eventId = Objects.requireNonNull(eventId, "eventId obrigatório");
        this.orderId = Objects.requireNonNull(orderId, "orderId obrigatório");
        this.patientId = Objects.requireNonNull(patientId, "patientId obrigatório");
        if (examCode == null || examCode.isBlank()) {
            throw new IllegalArgumentException("examCode obrigatório");
        }
        this.examCode = examCode.trim();
        this.status = Objects.requireNonNull(status, "status obrigatório");
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message obrigatória");
        }
        this.message = message.trim();
        this.createdAt = Instant.now();
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
