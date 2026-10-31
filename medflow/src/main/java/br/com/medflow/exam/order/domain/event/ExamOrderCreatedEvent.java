package br.com.medflow.exam.order.domain.event;

import br.com.medflow.exam.order.domain.ExamOrder;
import br.com.medflow.exam.order.domain.enums.ExamPriority;

import java.time.Instant;
import java.util.UUID;

/**
 * ============================================================================
 * CONTRATO DO EVENTO DE DOMÍNIO (EXAM ORDER CREATED EVENT)
 * ============================================================================
 * Este record define a estrutura de dados (DTO) que viaja pela rede do Kafka
 * quando um pedido de exame é criado no Medflow.
 *
 * Ele é um 'record' imutável contendo:
 * - eventId: O ID exclusivo DESTA mensagem (usado para deduplicação no consumidor).
 * - orderId: O ID do exame que foi criado no banco.
 * - patientId: O ID do paciente para quem o exame foi solicitado.
 * - examCode: O código do exame (ex: "HEMOGRAMA").
 * - priority: A prioridade médica (NORMAL ou URGENT).
 * - createdAt: Timestamp exato de quando o evento aconteceu.
 */
public record ExamOrderCreatedEvent(
        UUID eventId,
        UUID orderId,
        UUID patientId,
        String examCode,
        ExamPriority priority,
        Instant createdAt
) {
    /**
     * CONSTRUTOR DE NEGÓCIO:
     * Recebe diretamente a entidade ExamOrder recém-salva e extrai os campos dela.
     * Gera automaticamente o eventId único e a data atual (Instant.now()).
     * Funciona exatamente como o seu construtor de ExamOrderResponseDto(examOrder)!
     */
    public ExamOrderCreatedEvent(ExamOrder examOrder) {
        this(
                UUID.randomUUID(),
                examOrder.getId(),
                examOrder.getPatientId(),
                examOrder.getExamCode(),
                examOrder.getPriority(),
                Instant.now()
        );
    }

    /**
     * CONSTRUTOR AUXILIAR:
     * Usado principalmente nos testes unitários quando queremos criar um evento
     * passando os campos soltos sem precisar instanciar a entidade inteira.
     */
    public ExamOrderCreatedEvent(UUID orderId, UUID patientId, String examCode, ExamPriority priority) {
        this(
                UUID.randomUUID(),
                orderId,
                patientId,
                examCode,
                priority,
                Instant.now()
        );
    }
}
