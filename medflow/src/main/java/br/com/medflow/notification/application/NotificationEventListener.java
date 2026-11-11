package br.com.medflow.notification.application;

import br.com.medflow.exam.order.domain.enums.ExamStatus;
import br.com.medflow.exam.order.domain.event.ExamLaboratoryUpdatedEvent;
import br.com.medflow.messaging.consumer.domain.ProcessedEvent;
import br.com.medflow.messaging.consumer.persistence.ProcessedEventRepository;
import br.com.medflow.notification.domain.Notification;
import br.com.medflow.notification.persistence.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumidor de notificações no Apache Kafka.
 * Escuta atualizações enviadas pelo laboratório (tópico exam-notifications.v1),
 * verifica idempotência na tabela 'processed_events' e persiste avisos em 'notifications'.
 */
@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    public static final String CONSUMER_GROUP = "notification-group";

    private final NotificationRepository notificationRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    public NotificationEventListener(
            NotificationRepository notificationRepository,
            ProcessedEventRepository processedEventRepository,
            ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.processedEventRepository = processedEventRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${medflow.kafka.topic.exam-notifications:exam-notifications.v1}",
            groupId = CONSUMER_GROUP
    )
    @Transactional
    public void onLaboratoryUpdated(String messagePayload) throws Exception {
        ExamLaboratoryUpdatedEvent event = objectMapper.readValue(messagePayload, ExamLaboratoryUpdatedEvent.class);

        if (processedEventRepository.existsByEventIdAndConsumerGroup(event.eventId(), CONSUMER_GROUP)) {
            log.warn("Evento duplicado detectado: eventId={} para o grupo {}. Descartando com segurança.",
                    event.eventId(), CONSUMER_GROUP);
            return;
        }

        String message = buildMessage(event.examCode(), event.status());
        Notification notification = new Notification(
                event.eventId(),
                event.orderId(),
                event.patientId(),
                event.examCode(),
                event.status(),
                message
        );
        notificationRepository.save(notification);
        processedEventRepository.save(new ProcessedEvent(event.eventId(), CONSUMER_GROUP));

        log.info("Notificação persistida: pedido={}, status={}, eventId={}",
                event.orderId(), event.status(), event.eventId());
    }

    private String buildMessage(String examCode, ExamStatus status) {
        return switch (status) {
            case COLLECTED -> "Sua amostra para o exame " + examCode + " foi coletada pelo laboratório.";
            case RELEASED -> "O resultado do seu exame " + examCode + " foi liberado pelo laboratório.";
            case REJECTED, CANCELLED -> "Seu exame " + examCode + " foi encerrado com status " + status + ".";
            default -> "Status do exame " + examCode + " atualizado para " + status + ".";
        };
    }
}
