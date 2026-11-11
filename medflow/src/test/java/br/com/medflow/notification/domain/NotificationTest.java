package br.com.medflow.notification.domain;

import br.com.medflow.exam.order.domain.enums.ExamStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationTest {

    @Test
    void deveCriarNotificacaoComDadosNormalizados() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();

        Notification notification = new Notification(
                eventId,
                orderId,
                patientId,
                " HEMOGRAMA ",
                ExamStatus.COLLECTED,
                " Amostra coletada pelo laboratório. "
        );

        assertThat(notification.getEventId()).isEqualTo(eventId);
        assertThat(notification.getOrderId()).isEqualTo(orderId);
        assertThat(notification.getPatientId()).isEqualTo(patientId);
        assertThat(notification.getExamCode()).isEqualTo("HEMOGRAMA");
        assertThat(notification.getStatus()).isEqualTo(ExamStatus.COLLECTED);
        assertThat(notification.getMessage()).isEqualTo("Amostra coletada pelo laboratório.");
        assertThat(notification.getCreatedAt()).isNotNull();
    }

    @Test
    void naoDeveCriarNotificacaoQuandoCamposObrigatoriosForemNulos() {
        assertThatThrownBy(() -> new Notification(null, UUID.randomUUID(), UUID.randomUUID(), "HEMOGRAMA", ExamStatus.COLLECTED, "Msg"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Evento deve ser informado");

        assertThatThrownBy(() -> new Notification(UUID.randomUUID(), null, UUID.randomUUID(), "HEMOGRAMA", ExamStatus.COLLECTED, "Msg"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ordem de exame deve ser informada");

        assertThatThrownBy(() -> new Notification(UUID.randomUUID(), UUID.randomUUID(), null, "HEMOGRAMA", ExamStatus.COLLECTED, "Msg"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Paciente deve ser informado");

        assertThatThrownBy(() -> new Notification(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "HEMOGRAMA", null, "Msg"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Status do exame deve ser informado");
    }

    @Test
    void naoDeveCriarNotificacaoQuandoCodigoOuMensagemExcederemLimite() {
        assertThatThrownBy(() -> new Notification(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "A".repeat(51), ExamStatus.COLLECTED, "Msg"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Código do exame deve ter no máximo 50 caracteres");

        assertThatThrownBy(() -> new Notification(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "HEMOGRAMA", ExamStatus.COLLECTED, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Mensagem da notificação deve ser informada");

        assertThatThrownBy(() -> new Notification(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "HEMOGRAMA", ExamStatus.COLLECTED, "A".repeat(501)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Mensagem da notificação deve ter no máximo 500 caracteres");
    }
}
