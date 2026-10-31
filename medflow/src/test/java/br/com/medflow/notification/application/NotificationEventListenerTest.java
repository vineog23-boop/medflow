package br.com.medflow.notification.application;

import br.com.medflow.exam.order.domain.enums.ExamPriority;
import br.com.medflow.exam.order.domain.event.ExamOrderCreatedEvent;
import br.com.medflow.messaging.consumer.domain.ProcessedEvent;
import br.com.medflow.messaging.consumer.persistence.ProcessedEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationEventListenerTest {

    private ProcessedEventRepository processedEventRepository;
    private ObjectMapper objectMapper;
    private NotificationEventListener listener;

    @BeforeEach
    void setUp() {
        processedEventRepository = mock(ProcessedEventRepository.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        listener = new NotificationEventListener(processedEventRepository, objectMapper);
    }

    @Test
    @DisplayName("Deve processar evento inédito e registrar na tabela de processed_events")
    void shouldProcessNewEventAndSaveDeduplicationRecord() throws Exception {
        ExamOrderCreatedEvent event = new ExamOrderCreatedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "HEMO",
                ExamPriority.URGENT,
                Instant.now()
        );

        String json = objectMapper.writeValueAsString(event);

        when(processedEventRepository.existsByEventIdAndConsumerGroup(event.eventId(), "notification-group"))
                .thenReturn(false);

        listener.onExamOrderCreated(json);

        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    @DisplayName("Deve descartar evento duplicado sem processar novamente")
    void shouldIgnoreDuplicateEvent() throws Exception {
        ExamOrderCreatedEvent event = new ExamOrderCreatedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "HEMO",
                ExamPriority.NORMAL,
                Instant.now()
        );

        String json = objectMapper.writeValueAsString(event);

        when(processedEventRepository.existsByEventIdAndConsumerGroup(event.eventId(), "notification-group"))
                .thenReturn(true);

        listener.onExamOrderCreated(json);

        verify(processedEventRepository, never()).save(any(ProcessedEvent.class));
    }
}
