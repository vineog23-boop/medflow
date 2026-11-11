package br.com.medflow.exam.order.domain;

import br.com.medflow.exam.order.application.exception.ExamOrderStatusTransitionException;
import br.com.medflow.exam.order.domain.enums.ExamPriority;
import br.com.medflow.exam.order.domain.enums.ExamStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "exam_orders")
public class ExamOrder {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    @NotNull
    private UUID patientId;

    @Column(name = "exam_code", nullable = false, length = 50)
    @NotBlank
    private String examCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    @NotNull
    private ExamPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @NotNull
    private ExamStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected ExamOrder() {
        // Construtor usado pelo JPA para reconstruir registros do banco.
    }

    public ExamOrder(UUID patientId, String examCode, ExamPriority priority) {
        validateData(patientId, examCode);

        this.patientId = patientId;
        this.examCode = examCode.trim();

        if (priority == null) {
            this.priority = ExamPriority.NORMAL;
        } else {
            this.priority = priority;
        }

        this.status = ExamStatus.RECEIVED;

        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(UUID patientId, String examCode, ExamPriority priority) {
        validateData(patientId, examCode);

        this.patientId = patientId;
        this.examCode = examCode.trim();

        if (priority == null) {
            this.priority = ExamPriority.NORMAL;
        } else {
            this.priority = priority;
        }

        this.updatedAt = Instant.now();
    }

    /**
     * Aceita a ordem no laboratório movendo de RECEIVED para AWAITING_COLLECTION.
     * Mantém o status atual caso a ordem já tenha avançado (proteção contra eventos atrasados).
     */
    public boolean acceptByLaboratory() {
        if (this.status != ExamStatus.RECEIVED) {
            return false;
        }
        this.status = ExamStatus.AWAITING_COLLECTION;
        this.updatedAt = Instant.now();
        return true;
    }

    /**
     * Evolui a etapa operacional no laboratório, impedindo liberar resultado sem laudo (RELEASED)
     * ou alterar ordens já finalizadas.
     */
    public void changeStatus(ExamStatus targetStatus) {
        if (targetStatus == null) {
            throw new IllegalArgumentException("Status do exame deve ser informado");
        }
        if (isTerminalStatus()) {
            throw new ExamOrderStatusTransitionException(this.id, this.status, targetStatus);
        }
        if (targetStatus == ExamStatus.RECEIVED || targetStatus == ExamStatus.RELEASED) {
            throw new ExamOrderStatusTransitionException(this.id, this.status, targetStatus);
        }

        this.status = targetStatus;
        this.updatedAt = Instant.now();
    }

    /**
     * Conclui a entrega do exame ao registrar o laudo clínico (ExamResult).
     */
    public void markReleased() {
        if (isTerminalStatus()) {
            throw new ExamOrderStatusTransitionException(this.id, this.status, ExamStatus.RELEASED);
        }
        this.status = ExamStatus.RELEASED;
        this.updatedAt = Instant.now();
    }

    public boolean isTerminalStatus() {
        return this.status == ExamStatus.RELEASED
                || this.status == ExamStatus.REJECTED
                || this.status == ExamStatus.CANCELLED;
    }

    private static void validateData(UUID patientId, String examCode) {
        if (patientId == null) {
            throw new IllegalArgumentException("Paciente deve ser informado");
        }

        if (examCode == null || examCode.isBlank()) {
            throw new IllegalArgumentException("Código do exame deve ser informado");
        }

        if (examCode.trim().length() > 50) {
            throw new IllegalArgumentException(
                    "Código do exame deve ter no máximo 50 caracteres");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public String getExamCode() {
        return examCode;
    }

    public ExamPriority getPriority() {
        return priority;
    }

    public ExamStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
