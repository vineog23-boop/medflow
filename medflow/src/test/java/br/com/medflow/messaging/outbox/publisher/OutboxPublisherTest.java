package br.com.medflow.messaging.outbox.publisher;

import br.com.medflow.messaging.outbox.domain.OutboxEvent;
import br.com.medflow.messaging.outbox.domain.OutboxStatus;
import br.com.medflow.messaging.outbox.persistence.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxPublisherTest {

    private OutboxEventRepository repository;
    private KafkaTemplate<String, String> kafkaTemplate;
    private OutboxPublisher publisher;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(OutboxEventRepository.class);
        kafkaTemplate = (KafkaTemplate<String, String>) mock(KafkaTemplate.class);
        publisher = new OutboxPublisher(repository, kafkaTemplate, "exam-orders.v1");
    }

    @Test
    @DisplayName("Deve publicar eventos pendentes no Kafka e marcar status como PUBLISHED")
    void shouldPublishPendingEventsAndUpdateStatus() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String payload = "{\"orderId\":\"" + orderId + "\"}";

        OutboxEvent pendingEvent = new OutboxEvent(
                eventId,
                "EXAM_ORDER",
                orderId,
                "ExamOrderCreatedEvent",
                payload
        );

        when(repository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(pendingEvent));

        SendResult<String, String> sendResult = mock(SendResult.class);
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(sendResult);

        when(kafkaTemplate.send(eq("exam-orders.v1"), eq(orderId.toString()), eq(payload)))
                .thenReturn(future);

        publisher.publishPendingEvents();

        verify(kafkaTemplate).send("exam-orders.v1", orderId.toString(), payload);
        assertThat(pendingEvent.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(pendingEvent.getPublishedAt()).isNotNull();
        verify(repository).save(pendingEvent);
    }
}
