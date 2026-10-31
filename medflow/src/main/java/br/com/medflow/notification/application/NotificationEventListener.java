package br.com.medflow.notification.application;

import br.com.medflow.exam.order.domain.event.ExamOrderCreatedEvent;
import br.com.medflow.messaging.consumer.domain.ProcessedEvent;
import br.com.medflow.messaging.consumer.persistence.ProcessedEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * ============================================================================
 * CONSUMIDOR DE EVENTOS DE NOTIFICAÇÃO (KAFKA LISTENER)
 * ============================================================================
 * Esta classe escuta o tópico do Kafka onde os exames são anunciados e
 * simula o disparo de notificações (para a enfermaria, e-mail do paciente, etc).
 *
 * PONTO MAIS IMPORTANTE: IDEMPOTÊNCIA
 * No Kafka, uma mensagem pode ser entregue mais de uma vez devido a oscilações
 * de rede ou rebalanceamento do cluster.
 * Para garantir que o paciente NUNCA receba duas notificações do mesmo exame,
 * este listener consulta a tabela 'processed_events' antes de executar qualquer ação.
 */
@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    // Identificador único deste grupo de consumidores
    public static final String CONSUMER_GROUP = "notification-group";

    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    public NotificationEventListener(
            ProcessedEventRepository processedEventRepository,
            ObjectMapper objectMapper) {
        this.processedEventRepository = processedEventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * A anotação @KafkaListener faz o Spring registrar este método no Kafka.
     * Toda vez que uma mensagem chegar no tópico "exam-orders.v1", o Spring
     * acorda uma thread e passa o corpo da mensagem (JSON) no parâmetro 'messagePayload'.
     */
    @KafkaListener(topics = "${medflow.kafka.topic.exam-orders:exam-orders.v1}", groupId = CONSUMER_GROUP)
    @Transactional
    public void onExamOrderCreated(String messagePayload) throws Exception {

        // 1. DESSERIALIZAÇÃO: Converte o texto JSON puro que veio do Kafka de volta para o objeto Java
        ExamOrderCreatedEvent event = objectMapper.readValue(messagePayload, ExamOrderCreatedEvent.class);

        // 2. VERIFICAÇÃO DE IDEMPOTÊNCIA (Deduplicação):
        // Checa se o par (eventId + consumerGroup) já foi registrado na tabela 'processed_events'.
        if (processedEventRepository.existsByEventIdAndConsumerGroup(event.eventId(), CONSUMER_GROUP)) {
            log.warn("Evento duplicado detectado: eventId={} para o grupo {}. Descartando com segurança.",
                    event.eventId(), CONSUMER_GROUP);
            return; // Se já foi processado anteriormente, descarta e encerra o método imediatamente!
        }

        // 3. REGRA DE NEGÓCIO DO CONSUMIDOR:
        // Aqui é onde você chamaria o serviço de envio de e-mail, SMS ou painel da enfermaria
        log.info("Processando Notificação: Pedido={} criado para o Paciente={}. Exame={} [Prioridade={}]",
                event.orderId(), event.patientId(), event.examCode(), event.priority());

        // 4. REGISTRO DE IDEMPOTÊNCIA:
        // Salva na tabela 'processed_events' que este evento foi concluído com sucesso por este grupo.
        // Se a mesma mensagem chegar de novo daqui a 5 segundos, o passo 2 acima vai bloqueá-la.
        processedEventRepository.save(new ProcessedEvent(event.eventId(), CONSUMER_GROUP));
        log.info("Notificação enviada e evento registrado com sucesso: eventId={}", event.eventId());
    }
}
