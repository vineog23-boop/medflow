package br.com.medflow.messaging.outbox.publisher;

import br.com.medflow.messaging.outbox.domain.OutboxEvent;
import br.com.medflow.messaging.outbox.domain.OutboxStatus;
import br.com.medflow.messaging.outbox.persistence.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * ============================================================================
 * WORKER DE PUBLICAÇÃO DO OUTBOX (O CARTEIRO DO SISTEMA)
 * ============================================================================
 * Esta classe é responsável por tirar os eventos que foram salvos na tabela
 * 'outbox_events' do PostgreSQL e despachá-los para o broker do Apache Kafka.
 *
 * POR QUE USAMOS ESSA ABORDAGEM?
 * Se o Kafka estiver fora do ar temporariamente quando o médico solicitar um exame,
 * o pedido NÃO é perdido nem cancelado. Ele fica salvo na tabela do banco com
 * status PENDING. Assim que o Kafka volta, este worker pega a fila acumulada
 * e publica normalmente.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String examOrdersTopic;
    private final String examNotificationsTopic;

    public OutboxPublisher(
            OutboxEventRepository repository,
            KafkaTemplate<String, String> kafkaTemplate,
            String examOrdersTopic) {
        this(repository, kafkaTemplate, examOrdersTopic, "exam-notifications.v1");
    }

    @Autowired
    public OutboxPublisher(
            OutboxEventRepository repository,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${medflow.kafka.topic.exam-orders:exam-orders.v1}") String examOrdersTopic,
            @Value("${medflow.kafka.topic.exam-notifications:exam-notifications.v1}") String examNotificationsTopic) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.examOrdersTopic = examOrdersTopic;
        this.examNotificationsTopic = examNotificationsTopic;
    }

    /**
     * O Spring executa este método em segundo plano com um intervalo fixo.
     * fixedDelayString = 2000ms: espera 2 segundos após o término da execução anterior.
     */
    @Scheduled(fixedDelayString = "${medflow.outbox.poll-interval-ms:2000}")
    @Transactional
    public void publishPendingEvents() {
        // 1. Busca os primeiros 50 eventos com status PENDING, ordenados do mais antigo para o mais novo
        List<OutboxEvent> pendingEvents = repository.findByStatusOrderByCreatedAtAsc(
                OutboxStatus.PENDING,
                PageRequest.of(0, 50)
        );

        // Se não houver nada pendente na tabela, encerra o ciclo silenciosamente
        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Processando {} eventos pendentes na tabela outbox", pendingEvents.size());

        // 2. Itera sobre cada evento pendente para envio
        for (OutboxEvent event : pendingEvents) {
            String targetTopic = resolveTopic(event);
            try {
                // key: event.getAggregateId().toString() (o orderId do exame) -> Garante ordenação na partição!
                kafkaTemplate.send(targetTopic, event.getAggregateId().toString(), event.getPayload());

                event.markPublished(Instant.now());
                repository.save(event);
                log.info("Evento outbox {} publicado com sucesso no Kafka (tópico: {})", event.getId(), targetTopic);

            } catch (Exception e) {
                log.error("Falha ao publicar evento outbox {} no Kafka: {}", event.getId(), e.getMessage());
                event.markFailed();
                repository.save(event);
            }
        }
    }

    private String resolveTopic(OutboxEvent event) {
        if ("ExamLaboratoryUpdatedEvent".equals(event.getEventType())) {
            return examNotificationsTopic;
        }
        return examOrdersTopic;
    }
}
