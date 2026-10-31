package br.com.medflow.messaging;

import br.com.medflow.exam.definition.domain.ExamDefinition;
import br.com.medflow.exam.definition.domain.enums.SampleType;
import br.com.medflow.exam.definition.persistence.ExamDefinitionRepository;
import br.com.medflow.exam.order.application.ExamOrderService;
import br.com.medflow.exam.order.application.dto.request.CreateExamRequestDto;
import br.com.medflow.exam.order.application.dto.response.ExamOrderResponseDto;
import br.com.medflow.exam.order.domain.enums.ExamPriority;
import br.com.medflow.messaging.consumer.persistence.ProcessedEventRepository;
import br.com.medflow.messaging.outbox.domain.OutboxEvent;
import br.com.medflow.messaging.outbox.domain.OutboxStatus;
import br.com.medflow.messaging.outbox.persistence.OutboxEventRepository;
import br.com.medflow.messaging.outbox.publisher.OutboxPublisher;
import br.com.medflow.patient.domain.PatientEntity;
import br.com.medflow.patient.persistence.PatientRepository;
import br.com.medflow.support.KafkaTestContainerConfiguration;
import br.com.medflow.support.PostgreSqlTestContainerConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Import({PostgreSqlTestContainerConfiguration.class, KafkaTestContainerConfiguration.class})
class ExamOrderMessagingIntegrationTest {

    @Autowired
    private ExamOrderService examOrderService;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private ExamDefinitionRepository examDefinitionRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    @DisplayName("Fluxo E2E: Criação de pedido -> Registro no Outbox -> Publicação no Kafka -> Consumo idempotente")
    void shouldExecuteEndToEndMessagingFlow() {
        // 1. Criar pré-requisitos no banco
        PatientEntity patient = new PatientEntity(
                "Carlos Silva",
                "529.982.247-25",
                LocalDate.of(1990, 5, 15)
        );
        patientRepository.save(patient);

        ExamDefinition definition = new ExamDefinition(
                "HEMO_E2E",
                "Hemograma Completo E2E",
                SampleType.BLOOD
        );
        examDefinitionRepository.save(definition);

        // 2. Criar pedido de exame via serviço de negócio
        CreateExamRequestDto request = new CreateExamRequestDto(
                patient.getId(),
                "HEMO_E2E",
                ExamPriority.URGENT
        );
        ExamOrderResponseDto response = examOrderService.create(request);
        assertThat(response.id()).isNotNull();

        // 3. Verificar que o evento foi salvo no Outbox com status PENDING
        List<OutboxEvent> outboxList = outboxEventRepository.findAll();
        OutboxEvent outboxEvent = outboxList.stream()
                .filter(e -> e.getAggregateId().equals(response.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Evento Outbox não encontrado no banco"));

        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(outboxEvent.getEventType()).isEqualTo("ExamOrderCreatedEvent");

        // 4. Executar o worker publicador do Outbox
        outboxPublisher.publishPendingEvents();

        // 5. Verificar que o status no Outbox mudou para PUBLISHED
        OutboxEvent publishedEvent = outboxEventRepository.findById(outboxEvent.getId()).orElseThrow();
        assertThat(publishedEvent.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(publishedEvent.getPublishedAt()).isNotNull();

        // 6. Aguardar o consumidor processar e registrar idempotência na tabela processed_events
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            boolean processed = processedEventRepository.existsByEventIdAndConsumerGroup(
                    outboxEvent.getId(),
                    "notification-group"
            );
            assertThat(processed).isTrue();
        });

        // 7. Testar idempotência: reenviar o mesmo payload para o Kafka
        long countBefore = processedEventRepository.count();
        kafkaTemplate.send("exam-orders.v1", response.id().toString(), outboxEvent.getPayload());

        // Espera 3 segundos e valida que a contagem na tabela de deduplicação não aumentou
        await().pollDelay(3, TimeUnit.SECONDS).atMost(6, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(processedEventRepository.count()).isEqualTo(countBefore);
        });
    }
}
