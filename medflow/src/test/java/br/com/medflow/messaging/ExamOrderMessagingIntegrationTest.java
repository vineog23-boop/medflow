package br.com.medflow.messaging;

import br.com.medflow.exam.definition.domain.ExamDefinition;
import br.com.medflow.exam.definition.domain.enums.SampleType;
import br.com.medflow.exam.definition.persistence.ExamDefinitionRepository;
import br.com.medflow.exam.order.application.ExamOrderService;
import br.com.medflow.exam.order.application.dto.request.CreateExamRequestDto;
import br.com.medflow.exam.order.application.dto.request.CreateExamResultRequestDto;
import br.com.medflow.exam.order.application.dto.request.UpdateExamStatusRequestDto;
import br.com.medflow.exam.order.application.dto.response.ExamOrderResponseDto;
import br.com.medflow.exam.order.application.dto.response.ExamResultResponseDto;
import br.com.medflow.exam.order.domain.ExamOrder;
import br.com.medflow.exam.order.domain.enums.ExamPriority;
import br.com.medflow.exam.order.domain.enums.ExamStatus;
import br.com.medflow.exam.order.persistence.ExamOrderRepository;
import br.com.medflow.messaging.consumer.persistence.ProcessedEventRepository;
import br.com.medflow.messaging.outbox.domain.OutboxEvent;
import br.com.medflow.messaging.outbox.domain.OutboxStatus;
import br.com.medflow.messaging.outbox.persistence.OutboxEventRepository;
import br.com.medflow.messaging.outbox.publisher.OutboxPublisher;
import br.com.medflow.notification.application.NotificationService;
import br.com.medflow.notification.application.dto.response.NotificationResponseDto;
import br.com.medflow.notification.persistence.NotificationRepository;
import br.com.medflow.patient.domain.PatientEntity;
import br.com.medflow.patient.persistence.PatientRepository;
import br.com.medflow.support.KafkaTestContainerConfiguration;
import br.com.medflow.support.PostgreSqlTestContainerConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDate;
import java.util.List;
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
    private ExamOrderRepository examOrderRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    @DisplayName("Fluxo E2E: Pedido Médico -> Kafka Laboratório (AWAITING_COLLECTION) -> Coleta & Resultado -> Kafka Notificações -> Idempotência")
    void shouldExecuteEndToEndMessagingFlow() {
        // 1. Pré-requisitos: Recepção cadastra Paciente e Laboratório cadastra definição no catálogo
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

        // 2. Médico cria pedido -> nasce RECEIVED + Outbox PENDING
        CreateExamRequestDto request = new CreateExamRequestDto(
                patient.getId(),
                "HEMO_E2E",
                ExamPriority.URGENT
        );
        ExamOrderResponseDto response = examOrderService.create(request);
        assertThat(response.status()).isEqualTo(ExamStatus.RECEIVED);

        OutboxEvent createdOutbox = outboxEventRepository.findAll().stream()
                .filter(e -> e.getAggregateId().equals(response.id())
                        && "ExamOrderCreatedEvent".equals(e.getEventType()))
                .findFirst()
                .orElseThrow();

        // 3. OutboxPublisher envia para exam-orders.v1 -> Laboratório aceita via Kafka e muda para AWAITING_COLLECTION
        outboxPublisher.publishPendingEvents();

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(processedEventRepository.existsByEventIdAndConsumerGroup(
                    createdOutbox.getId(), "laboratory-group")).isTrue();
            ExamOrder acceptedOrder = examOrderRepository.findById(response.id()).orElseThrow();
            assertThat(acceptedOrder.getStatus()).isEqualTo(ExamStatus.AWAITING_COLLECTION);
        });

        // 4. Laboratório registra coleta (COLLECTED) e depois entrega o resultado (ExamResult + RELEASED)
        ExamOrderResponseDto collected = examOrderService.updateStatus(
                response.id(),
                new UpdateExamStatusRequestDto(ExamStatus.COLLECTED)
        );
        assertThat(collected.status()).isEqualTo(ExamStatus.COLLECTED);

        ExamResultResponseDto examResult = examOrderService.registerResult(
                response.id(),
                new CreateExamResultRequestDto("Hemácias e leucócitos dentro da faixa de referência.")
        );
        assertThat(examResult.orderId()).isEqualTo(response.id());

        ExamResultResponseDto fetchedResult = examOrderService.findResultByOrderId(response.id());
        assertThat(fetchedResult.resultText()).contains("faixa de referência");

        List<OutboxEvent> labOutboxEvents = outboxEventRepository.findAll().stream()
                .filter(e -> e.getAggregateId().equals(response.id())
                        && "ExamLaboratoryUpdatedEvent".equals(e.getEventType())
                        && e.getStatus() == OutboxStatus.PENDING)
                .toList();
        assertThat(labOutboxEvents).hasSize(2);

        // 5. OutboxPublisher envia evoluções do Laboratório para exam-notifications.v1
        outboxPublisher.publishPendingEvents();

        // 6. NotificationEventListener consome e persiste notificações reais consultáveis por Paciente
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            for (OutboxEvent labEvent : labOutboxEvents) {
                assertThat(processedEventRepository.existsByEventIdAndConsumerGroup(
                        labEvent.getId(), "notification-group")).isTrue();
            }
            Page<NotificationResponseDto> notifications = notificationService.findAll(
                    patient.getId(),
                    PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))
            );
            assertThat(notifications.getTotalElements()).isEqualTo(2);
            assertThat(notifications.getContent().get(0).status()).isEqualTo(ExamStatus.RELEASED);
            assertThat(notifications.getContent().get(1).status()).isEqualTo(ExamStatus.COLLECTED);
        });

        // 7. Idempotência: reenviar o mesmo evento em exam-notifications.v1 não cria notificação duplicada
        long notifCountBefore = notificationRepository.count();
        long processedCountBefore = processedEventRepository.count();
        OutboxEvent duplicateCandidate = labOutboxEvents.getFirst();
        kafkaTemplate.send("exam-notifications.v1", response.id().toString(), duplicateCandidate.getPayload());

        await().pollDelay(3, TimeUnit.SECONDS).atMost(6, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(notificationRepository.count()).isEqualTo(notifCountBefore);
            assertThat(processedEventRepository.count()).isEqualTo(processedCountBefore);
        });
    }
}
