package br.com.medflow.messaging.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * ============================================================================
 * CLASSE CENTRAL DE CONFIGURAÇÃO DO APACHE KAFKA
 * ============================================================================
 * Esta classe é o "painel de controle" do Kafka no Medflow.
 * A anotação @Configuration indica ao Spring que aqui são definidos os Beans (componentes).
 * A anotação @EnableKafka ativa a infraestrutura para que a anotação @KafkaListener funcione.
 *
 * O QUE ESSA CLASSE CONFIGURA:
 * 1. PRODUCER (Quem envia mensagens): ProducerFactory e KafkaTemplate.
 * 2. CONSUMER (Quem escuta mensagens): ConsumerFactory e ContainerFactory.
 * 3. RESILIÊNCIA / DLT (Tratamento de falhas): Retentativas e Dead Letter Topic.
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    // Lê o endereço do broker Kafka a partir do application.properties (padrão: localhost:9092)
    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    // ------------------------------------------------------------------------
    // 1. PRODUCER (LADO DO ENVIO)
    // ------------------------------------------------------------------------

    /**
     * PRODUCER FACTORY (A Montadora de Conexões de Envio):
     * Define como o cliente do Kafka deve se comportar ao enviar mensagens:
     * - Onde o servidor está rodando (BOOTSTRAP_SERVERS_CONFIG).
     * - Como converter a chave da mensagem para bytes (StringSerializer).
     * - Como converter o corpo da mensagem (JSON) para bytes (StringSerializer).
     * - Nível de confirmação de escrita (ACKS_CONFIG = "all", garante persistência máxima).
     */
    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        return new DefaultKafkaProducerFactory<>(props);
    }

    /**
     * KAFKATEMPLATE (O Carro / A Ferramenta de Envio):
     * É o objeto que usamos no código do dia a dia (ex: no OutboxPublisher)
     * para disparar o método: kafkaTemplate.send(topico, chave, valor).
     */
    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    // ------------------------------------------------------------------------
    // 2. CONSUMER (LADO DA RECEPÇÃO)
    // ------------------------------------------------------------------------

    /**
     * CONSUMER FACTORY (A Montadora de Conexões de Escuta):
     * Define como o cliente do Kafka deve se comportar ao escutar tópicos:
     * - Onde o servidor está rodando (BOOTSTRAP_SERVERS_CONFIG).
     * - Ler desde o início da fila se for a primeira vez (AUTO_OFFSET_RESET_CONFIG = "earliest").
     * - Como ler os bytes que chegam da rede e transformá-los em String (StringDeserializer).
     * - O groupId é definido explicitamente em cada @KafkaListener.
     */
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * CONTAINER FACTORY (O Motor de Segundo Plano dos Listeners):
     * O Spring usa essa fábrica para criar a thread silenciosa que fica
     * monitorando as partições do Kafka e chamando os métodos anotados com @KafkaListener.
     * Também acoplamos a ele o nosso 'errorHandler' para tratar erros automaticamente.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            CommonErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    // ------------------------------------------------------------------------
    // 3. RESILIÊNCIA / DEAD LETTER TOPIC (DLT)
    // ------------------------------------------------------------------------

    /**
     * ERROR HANDLER (Tratamento de Falhas e Desvio para DLT):
     * Se um consumidor (ex: NotificationEventListener) lançar uma exceção não esperada:
     * 1. O Spring não descarta a mensagem na primeira tentativa.
     * 2. Ele tenta novamente com intervalo exponencial: espera 1 segundo, depois 2 segundos.
     * 3. Se após as tentativas o erro persistir, o recoverer desvia a mensagem para um
     *    tópico de cartas mortas (.DLT), evitando travar a partição e o sistema.
     */
    @Bean
    public CommonErrorHandler errorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> {
                    log.error("Tentativas esgotadas para a mensagem. Encaminhando para DLT: topico={}, chave={}, erro={}",
                            record.topic(), record.key(), exception.getMessage());
                    return new TopicPartition(record.topic() + ".DLT", record.partition());
                }
        );

        // Configuração de Backoff Exponencial (esperas progressivas de 1s, 2s... até no máximo 5s)
        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxElapsedTime(5000L);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

        // Exceções de argumento inválido (erro de negócio irrecuperável) não devem sofrer retentativa
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);

        return errorHandler;
    }
}
