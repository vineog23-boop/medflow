package br.com.medflow.notification.application;

import br.com.medflow.exam.order.domain.enums.ExamStatus;
import br.com.medflow.exam.order.domain.event.ExamLaboratoryUpdatedEvent;
import br.com.medflow.messaging.consumer.domain.ProcessedEvent;
import br.com.medflow.messaging.consumer.persistence.ProcessedEventRepository;
import br.com.medflow.notification.domain.Notification;
import br.com.medflow.notification.persistence.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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

    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final ProcessedEventRepository processedEventRepository = mock(ProcessedEventRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final NotificationEventListener listener = new NotificationEventListener(
            notificationRepository,
            processedEventRepository,
            objectMapper
    );

    @Test
    void devePersistirNotificacaoERegistrarDeduplicacaoQuandoConsumirAtualizacao() throws Exception {
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
    void naoDeveCriarNotificacaoQuandoEventoForDuplicado() throws Exception {
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

    @Test
    void deveGerarMensagemAmigavelParaEtapasIntermediariasEEncerramentos() throws Exception {
        ExamLaboratoryUpdatedEvent inTransitEvent = new ExamLaboratoryUpdatedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "GLICOSE",
                ExamStatus.IN_TRANSIT,
                Instant.now()
        );
        when(processedEventRepository.existsByEventIdAndConsumerGroup(inTransitEvent.eventId(), "notification-group"))
                .thenReturn(false);

        listener.onLaboratoryUpdated(objectMapper.writeValueAsString(inTransitEvent));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getMessage())
                .contains("em transporte")
                .doesNotContain("IN_TRANSIT");
    }
}
