package br.com.medflow.exam.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Representa o laudo/resultado clínico entregue pelo laboratório para o paciente.
 * Fica separado de ExamOrder porque contém dados clínicos de leitura do cliente.
 */
@Entity
@Table(name = "exam_results")
public class ExamResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    @NotNull
    private UUID orderId;

    @Column(name = "patient_id", nullable = false)
    @NotNull
    private UUID patientId;

    @NotBlank
    @Size(max = 50)
    @Column(name = "exam_code", nullable = false, length = 50)
    private String examCode;

    @NotBlank
    @Column(name = "result_text", nullable = false, columnDefinition = "TEXT")
    private String resultText;

    @NotNull
    @Column(name = "released_at", nullable = false, updatable = false)
    private Instant releasedAt;

    protected ExamResult() {
    }

    public ExamResult(UUID orderId, UUID patientId, String examCode, String resultText) {
        validateData(orderId, patientId, examCode, resultText);

        this.orderId = orderId;
        this.patientId = patientId;
        this.examCode = examCode.trim();
        this.resultText = resultText.trim();
        this.releasedAt = Instant.now();
    }

    private static void validateData(UUID orderId, UUID patientId, String examCode, String resultText) {
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
        if (resultText == null || resultText.isBlank()) {
            throw new IllegalArgumentException("Texto do resultado deve ser informado");
        }
    }

    public UUID getId() {
        return id;
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

    public String getResultText() {
        return resultText;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }
}
