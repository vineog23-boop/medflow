package br.com.medflow.laboratory.application;

import br.com.medflow.exam.order.domain.ExamOrder;
import br.com.medflow.exam.order.domain.enums.ExamPriority;
import br.com.medflow.exam.order.domain.enums.ExamStatus;
import br.com.medflow.exam.order.domain.event.ExamOrderCreatedEvent;
import br.com.medflow.exam.order.persistence.ExamOrderRepository;
import br.com.medflow.messaging.consumer.domain.ProcessedEvent;
import br.com.medflow.messaging.consumer.persistence.ProcessedEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LaboratoryEventListenerTest {

    private final ExamOrderRepository examOrderRepository = mock(ExamOrderRepository.class);
    private final ProcessedEventRepository processedEventRepository = mock(ProcessedEventRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final LaboratoryEventListener listener = new LaboratoryEventListener(
            examOrderRepository,
            processedEventRepository,
            objectMapper
    );

    @Test
    void deveAceitarOrdemEMudarStatusParaAwaitingCollection() throws Exception {
        UUID orderId = UUID.randomUUID();
        ExamOrder examOrder = new ExamOrder(UUID.randomUUID(), "HEMOGRAMA", ExamPriority.URGENT);
        ReflectionTestUtils.setField(examOrder, "id", orderId);

        ExamOrderCreatedEvent event = new ExamOrderCreatedEvent(
                UUID.randomUUID(),
                orderId,
                examOrder.getPatientId(),
                "HEMOGRAMA",
                ExamPriority.URGENT,
                Instant.now()
        );
        String json = objectMapper.writeValueAsString(event);

        when(processedEventRepository.existsByEventIdAndConsumerGroup(event.eventId(), "laboratory-group"))
                .thenReturn(false);
        when(examOrderRepository.findById(orderId)).thenReturn(Optional.of(examOrder));

        listener.onExamOrderCreated(json);

        assertThat(examOrder.getStatus()).isEqualTo(ExamStatus.AWAITING_COLLECTION);
        verify(examOrderRepository).save(examOrder);
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void deveIgnorarEventoDuplicadoNoLaboratorio() throws Exception {
        UUID orderId = UUID.randomUUID();
        ExamOrderCreatedEvent event = new ExamOrderCreatedEvent(
                UUID.randomUUID(),
                orderId,
                UUID.randomUUID(),
                "HEMOGRAMA",
                ExamPriority.NORMAL,
                Instant.now()
        );
        String json = objectMapper.writeValueAsString(event);

        when(processedEventRepository.existsByEventIdAndConsumerGroup(event.eventId(), "laboratory-group"))
                .thenReturn(true);

        listener.onExamOrderCreated(json);

        verify(examOrderRepository, never()).findById(any());
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void deveManterStatusAtualQuandoEventoChegarAtrasado() throws Exception {
        UUID orderId = UUID.randomUUID();
        ExamOrder examOrder = new ExamOrder(UUID.randomUUID(), "HEMOGRAMA", ExamPriority.NORMAL);
        ReflectionTestUtils.setField(examOrder, "id", orderId);
        examOrder.changeStatus(ExamStatus.COLLECTED);

        ExamOrderCreatedEvent event = new ExamOrderCreatedEvent(
                UUID.randomUUID(),
                orderId,
                examOrder.getPatientId(),
                "HEMOGRAMA",
                ExamPriority.NORMAL,
                Instant.now()
        );
        String json = objectMapper.writeValueAsString(event);

        when(processedEventRepository.existsByEventIdAndConsumerGroup(event.eventId(), "laboratory-group"))
                .thenReturn(false);
        when(examOrderRepository.findById(orderId)).thenReturn(Optional.of(examOrder));

        listener.onExamOrderCreated(json);

        assertThat(examOrder.getStatus()).isEqualTo(ExamStatus.COLLECTED);
        verify(examOrderRepository, never()).save(any());
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }
}
