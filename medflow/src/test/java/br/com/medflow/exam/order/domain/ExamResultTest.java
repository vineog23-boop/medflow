package br.com.medflow.exam.order.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExamResultTest {

    @Test
    void deveCriarResultadoComDadosNormalizados() {
        UUID orderId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();

        ExamResult result = new ExamResult(orderId, patientId, " HEMOGRAMA ", " Sem alterações ");

        assertThat(result.getOrderId()).isEqualTo(orderId);
        assertThat(result.getPatientId()).isEqualTo(patientId);
        assertThat(result.getExamCode()).isEqualTo("HEMOGRAMA");
        assertThat(result.getResultText()).isEqualTo("Sem alterações");
        assertThat(result.getReleasedAt()).isNotNull();
    }

    @Test
    void naoDeveCriarResultadoSemOrdemOuPaciente() {
        assertThatThrownBy(() -> new ExamResult(null, UUID.randomUUID(), "HEMOGRAMA", "Laudo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ordem de exame deve ser informada");

        assertThatThrownBy(() -> new ExamResult(UUID.randomUUID(), null, "HEMOGRAMA", "Laudo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Paciente deve ser informado");
    }

    @Test
    void naoDeveCriarResultadoComCodigoInvalidoOuTextoVazio() {
        assertThatThrownBy(() -> new ExamResult(UUID.randomUUID(), UUID.randomUUID(), "A".repeat(51), "Laudo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Código do exame deve ter no máximo 50 caracteres");

        assertThatThrownBy(() -> new ExamResult(UUID.randomUUID(), UUID.randomUUID(), "HEMOGRAMA", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Texto do resultado deve ser informado");
    }
}
