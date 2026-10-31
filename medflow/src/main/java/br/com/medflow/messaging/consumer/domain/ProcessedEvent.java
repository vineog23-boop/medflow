package br.com.medflow.messaging.consumer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * ============================================================================
 * ENTIDADE DE CONTROLE DE IDEMPOTÊNCIA (PROCESSED_EVENTS)
 * ============================================================================
 * Esta tabela guarda o histórico de quais eventos já foram processados
 * por cada grupo consumidor no sistema.
 *
 * Chave Primária Composta (ProcessedEventId):
 * - eventId: O identificador único da mensagem do Kafka.
 * - consumerGroup: O nome do grupo que consumiu (ex: "notification-group").
 *
 * POR QUE O CONSUMER_GROUP FAZ PARTE DA CHAVE?
 * Porque se amanhã criarmos outro serviço consumidor (ex: "faturamento-group"),
 * ele também precisará processar o mesmo exame pela primeira vez.
 * Cada grupo mantém o seu próprio controle de deduplicação!
 */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @EmbeddedId
    private ProcessedEventId id;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEvent() {
    }

    public ProcessedEvent(UUID eventId, String consumerGroup) {
        this.id = new ProcessedEventId(eventId, consumerGroup);
        this.processedAt = Instant.now();
    }

    public ProcessedEventId getId() {
        return id;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProcessedEvent that = (ProcessedEvent) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
