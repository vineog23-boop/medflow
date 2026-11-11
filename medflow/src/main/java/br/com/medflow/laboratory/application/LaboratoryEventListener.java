package br.com.medflow.laboratory.application;

import br.com.medflow.exam.order.application.exception.ExamOrderNotFoundException;
import br.com.medflow.exam.order.domain.ExamOrder;
import br.com.medflow.exam.order.domain.event.ExamOrderCreatedEvent;
import br.com.medflow.exam.order.persistence.ExamOrderRepository;
import br.com.medflow.messaging.consumer.domain.ProcessedEvent;
import br.com.medflow.messaging.consumer.persistence.ProcessedEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumidor do Laboratório no Apache Kafka.
 * Recebe solicitações emitidas pelos médicos no tópico exam-orders.v1 e coloca o pedido
 * em espera de coleta (AWAITING_COLLECTION) de forma idempotente.
 */
@Component
public class LaboratoryEventListener {

    private static final Logger log = LoggerFactory.getLogger(LaboratoryEventListener.class);

    public static final String CONSUMER_GROUP = "laboratory-group";

    private final ExamOrderRepository examOrderRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    public LaboratoryEventListener(
            ExamOrderRepository examOrderRepository,
            ProcessedEventRepository processedEventRepository,
            ObjectMapper objectMapper) {
        this.examOrderRepository = examOrderRepository;
        this.processedEventRepository = processedEventRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${medflow.kafka.topic.exam-orders:exam-orders.v1}", groupId = CONSUMER_GROUP)
    @Transactional
    public void onExamOrderCreated(String messagePayload) throws Exception {
        ExamOrderCreatedEvent event = objectMapper.readValue(messagePayload, ExamOrderCreatedEvent.class);

        if (processedEventRepository.existsByEventIdAndConsumerGroup(event.eventId(), CONSUMER_GROUP)) {
            log.warn("Evento duplicado descartado no laboratório: eventId={}", event.eventId());
            return;
        }

        ExamOrder examOrder = examOrderRepository.findById(event.orderId())
                .orElseThrow(() -> new ExamOrderNotFoundException(event.orderId()));

        if (examOrder.acceptByLaboratory()) {
            examOrderRepository.save(examOrder);
            log.info("Laboratório aceitou a ordem {} (status={})", examOrder.getId(), examOrder.getStatus());
        } else {
            log.info("Ordem {} já está em etapa {}, mantendo status atual", examOrder.getId(), examOrder.getStatus());
        }

        processedEventRepository.save(new ProcessedEvent(event.eventId(), CONSUMER_GROUP));
    }
}
