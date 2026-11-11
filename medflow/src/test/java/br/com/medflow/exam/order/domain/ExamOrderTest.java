package br.com.medflow.exam.order.domain;

import br.com.medflow.exam.order.application.exception.ExamOrderStatusTransitionException;
import br.com.medflow.exam.order.domain.enums.ExamPriority;
import br.com.medflow.exam.order.domain.enums.ExamStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifica as invariantes aplicadas na criação de uma ordem de exame.
 *
 * Estes testes são unitários porque avaliam somente o comportamento do domínio,
 * sem depender de Spring, JPA ou banco de dados.
 */
class ExamOrderTest {

    @Test
    void deveCriarOrdemComValoresIniciaisDoDominio() {
        // Arrange: a prioridade não informada deve receber o valor padrão.
        UUID patientId = UUID.randomUUID();

        // Act
        ExamOrder examOrder = new ExamOrder(patientId, "HEMOGRAMA", null);

        // Assert: a entidade deve nascer em um estado válido para o workflow.
        assertThat(examOrder.getPatientId()).isEqualTo(patientId);
        assertThat(examOrder.getExamCode()).isEqualTo("HEMOGRAMA");
        assertThat(examOrder.getPriority()).isEqualTo(ExamPriority.NORMAL);
        assertThat(examOrder.getStatus()).isEqualTo(ExamStatus.RECEIVED);
        assertThat(examOrder.getCreatedAt()).isNotNull();
        assertThat(examOrder.getUpdatedAt()).isEqualTo(examOrder.getCreatedAt());
    }

    @Test
    void devePreservarPrioridadeInformada() {
        // Arrange e Act
        ExamOrder examOrder = new ExamOrder(UUID.randomUUID(), "GLICOSE", ExamPriority.URGENT);

        // Assert
        assertThat(examOrder.getPriority()).isEqualTo(ExamPriority.URGENT);
    }

    @Test
    void naoDeveCriarOrdemSemPaciente() {
        // A ausência do paciente viola uma regra obrigatória do domínio.
        assertThatThrownBy(() -> new ExamOrder(null, "HEMOGRAMA", ExamPriority.NORMAL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void naoDeveCriarOrdemComCodigoVazio() {
        // O código identifica o exame e não pode ser vazio ou composto apenas por espaços.
        assertThatThrownBy(() -> new ExamOrder(UUID.randomUUID(), "  ", ExamPriority.NORMAL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void naoDeveCriarOrdemComCodigoMaiorQueCinquentaCaracteres() {
        // Act e Assert: 51 caracteres ultrapassam o limite compartilhado com a API e o banco.
        assertThatThrownBy(() -> new ExamOrder(
                UUID.randomUUID(),
                "A".repeat(51),
                ExamPriority.NORMAL
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Código do exame deve ter no máximo 50 caracteres");
    }

    @Test
    void naoDeveAtualizarOrdemSemPaciente() {
        // Arrange: cria uma ordem válida antes de tentar uma atualização inválida.
        ExamOrder examOrder = new ExamOrder(
                UUID.randomUUID(),
                "HEMOGRAMA",
                ExamPriority.NORMAL
        );

        // Act e Assert: a atualização deve aplicar a mesma regra usada na criação.
        assertThatThrownBy(() -> examOrder.update(
                null,
                "GLICOSE",
                ExamPriority.URGENT
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Paciente deve ser informado");
    }

    @Test
    void naoDeveAtualizarOrdemComCodigoVazio() {
        // Arrange: cria uma ordem válida antes de tentar uma atualização inválida.
        ExamOrder examOrder = new ExamOrder(
                UUID.randomUUID(),
                "HEMOGRAMA",
                ExamPriority.NORMAL
        );

        // Act e Assert: espaços não representam um código de exame válido.
        assertThatThrownBy(() -> examOrder.update(
                UUID.randomUUID(),
                "   ",
                ExamPriority.URGENT
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Código do exame deve ser informado");
    }

    @Test
    void naoDeveAtualizarOrdemComCodigoMaiorQueCinquentaCaracteres() {
        // Arrange: cria uma ordem válida antes de testar a fronteira na atualização.
        ExamOrder examOrder = new ExamOrder(
                UUID.randomUUID(),
                "HEMOGRAMA",
                ExamPriority.NORMAL
        );

        // Act e Assert: a atualização deve preservar a mesma invariante da criação.
        assertThatThrownBy(() -> examOrder.update(
                UUID.randomUUID(),
                "A".repeat(51),
                ExamPriority.URGENT
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Código do exame deve ter no máximo 50 caracteres");
    }

    @Test
    void deveAceitarOrdemNoLaboratorioQuandoEstiverEmReceived() {
        ExamOrder examOrder = new ExamOrder(UUID.randomUUID(), "HEMOGRAMA", ExamPriority.NORMAL);

        boolean changed = examOrder.acceptByLaboratory();

        assertThat(changed).isTrue();
        assertThat(examOrder.getStatus()).isEqualTo(ExamStatus.AWAITING_COLLECTION);
    }

    @Test
    void naoDeveRetrocederStatusAoAceitarOrdemJaEmEtapaPosterior() {
        ExamOrder examOrder = new ExamOrder(UUID.randomUUID(), "HEMOGRAMA", ExamPriority.NORMAL);
        examOrder.changeStatus(ExamStatus.COLLECTED);

        boolean changed = examOrder.acceptByLaboratory();

        assertThat(changed).isFalse();
        assertThat(examOrder.getStatus()).isEqualTo(ExamStatus.COLLECTED);
    }

    @Test
    void deveEvoluirEtapasOperacionaisERejeitarReleasedDiretoOuOrdemFinalizada() {
        ExamOrder examOrder = new ExamOrder(UUID.randomUUID(), "HEMOGRAMA", ExamPriority.NORMAL);

        examOrder.changeStatus(ExamStatus.COLLECTED);
        assertThat(examOrder.getStatus()).isEqualTo(ExamStatus.COLLECTED);

        assertThatThrownBy(() -> examOrder.changeStatus(ExamStatus.RELEASED))
                .isInstanceOf(ExamOrderStatusTransitionException.class);

        examOrder.markReleased();
        assertThat(examOrder.getStatus()).isEqualTo(ExamStatus.RELEASED);

        assertThatThrownBy(() -> examOrder.changeStatus(ExamStatus.PROCESSING))
                .isInstanceOf(ExamOrderStatusTransitionException.class);
    }

    @Test
    void naoDeveAlterarStatusParaReceived() {
        ExamOrder examOrder = new ExamOrder(UUID.randomUUID(), "HEMOGRAMA", ExamPriority.NORMAL);
        examOrder.acceptByLaboratory();

        assertThatThrownBy(() -> examOrder.changeStatus(ExamStatus.RECEIVED))
                .isInstanceOf(ExamOrderStatusTransitionException.class);
    }

    @Test
    void naoDeveAlterarStatusQuandoOrdemEstiverRejeitadaOuCancelada() {
        ExamOrder rejectedOrder = new ExamOrder(UUID.randomUUID(), "HEMOGRAMA", ExamPriority.NORMAL);
        rejectedOrder.changeStatus(ExamStatus.REJECTED);

        assertThatThrownBy(() -> rejectedOrder.changeStatus(ExamStatus.PROCESSING))
                .isInstanceOf(ExamOrderStatusTransitionException.class);

        ExamOrder cancelledOrder = new ExamOrder(UUID.randomUUID(), "HEMOGRAMA", ExamPriority.NORMAL);
        cancelledOrder.changeStatus(ExamStatus.CANCELLED);

        assertThatThrownBy(() -> cancelledOrder.changeStatus(ExamStatus.COLLECTED))
                .isInstanceOf(ExamOrderStatusTransitionException.class);
    }
}
