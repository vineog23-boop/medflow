package br.com.medflow.notification.application;

import br.com.medflow.exam.order.domain.enums.ExamStatus;
import br.com.medflow.exam.order.domain.event.ExamLaboratoryUpdatedEvent;
import br.com.medflow.messaging.consumer.domain.ProcessedEvent;
import br.com.medflow.messaging.consumer.persistence.ProcessedEventRepository;
import br.com.medflow.notification.domain.Notification;
import br.com.medflow.notification.persistence.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationEventListenerTest {

    private NotificationRepository notificationRepository;
    private ProcessedEventRepository processedEventRepository;
    private ObjectMapper objectMapper;
    private NotificationEventListener listener;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        processedEventRepository = mock(ProcessedEventRepository.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        listener = new NotificationEventListener(notificationRepository, processedEventRepository, objectMapper);
    }

    @Test
    @DisplayName("Deve persistir notificação e registrar deduplicação ao consumir atualização do laboratório")
    void shouldPersistNotificationAndSaveDeduplicationRecord() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        ExamLaboratoryUpdatedEvent event = new ExamLaboratoryUpdatedEvent(
                UUID.randomUUID(),
                orderId,
                patientId,
                "HEMOGRAMA",
                ExamStatus.RELEASED,
                Instant.now()
        );
        String json = objectMapper.writeValueAsString(event);

        when(processedEventRepository.existsByEventIdAndConsumerGroup(event.eventId(), "notification-group"))
                .thenReturn(false);

        listener.onLaboratoryUpdated(json);

        ArgumentCaptor<Notification> notifCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notifCaptor.capture());
        Notification saved = notifCaptor.getValue();
        assertThat(saved.getOrderId()).isEqualTo(orderId);
        assertThat(saved.getPatientId()).isEqualTo(patientId);
        assertThat(saved.getExamCode()).isEqualTo("HEMOGRAMA");
        assertThat(saved.getStatus()).isEqualTo(ExamStatus.RELEASED);
        assertThat(saved.getMessage()).contains("liberado");
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    @DisplayName("Deve descartar evento duplicado sem criar nova notificação")
    void shouldIgnoreDuplicateEvent() throws Exception {
        ExamLaboratoryUpdatedEvent event = new ExamLaboratoryUpdatedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "HEMOGRAMA",
                ExamStatus.COLLECTED,
                Instant.now()
        );
        String json = objectMapper.writeValueAsString(event);

        when(processedEventRepository.existsByEventIdAndConsumerGroup(event.eventId(), "notification-group"))
                .thenReturn(true);

        listener.onLaboratoryUpdated(json);

        verify(notificationRepository, never()).save(any(Notification.class));
        verify(processedEventRepository, never()).save(any(ProcessedEvent.class));
    }
}
