package br.com.medflow.messaging.outbox.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * ============================================================================
 * ENTIDADE DA TABELA OUTBOX (A CAIXA DE SAÍDA NO POSTGRESQL)
 * ============================================================================
 * Esta tabela é o coração do padrão Transactional Outbox.
 * Quando um médico cria um pedido de exame, nós salvamos o pedido na tabela
 * 'exam_orders' E salvamos uma linha nesta tabela 'outbox_events', sob a
 * MESMA transação do PostgreSQL (@Transactional).
 *
 * CAMPOS PRINCIPAIS:
 * - id: Identificador único da mensagem (será o eventId no Kafka).
 * - aggregateType: O tipo do domínio (ex: "EXAM_ORDER").
 * - aggregateId: O ID do exame criado (usado como chave de partição no Kafka!).
 * - eventType: O nome do evento (ex: "ExamOrderCreatedEvent").
 * - payload: O JSON com todos os dados do exame prontos para envio.
 * - status: PENDING (aguardando envio), PUBLISHED (já enviado), FAILED (erro).
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(UUID id, String aggregateType, UUID aggregateId, String eventType, String payload) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public void markPublished(Instant publishedAt) {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = publishedAt;
    }

    public void markFailed() {
        this.status = OutboxStatus.FAILED;
    }

    public UUID getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
