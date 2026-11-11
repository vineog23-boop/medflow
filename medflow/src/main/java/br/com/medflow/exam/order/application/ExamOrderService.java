package br.com.medflow.exam.order.application;

import br.com.medflow.exam.definition.application.exception.ExamDefinitionInactiveException;
import br.com.medflow.exam.definition.application.exception.ExamDefinitionNotFoundException;
import br.com.medflow.exam.definition.domain.ExamDefinition;
import br.com.medflow.exam.definition.persistence.ExamDefinitionRepository;
import br.com.medflow.exam.order.application.dto.request.CreateExamRequestDto;
import br.com.medflow.exam.order.application.dto.request.CreateExamResultRequestDto;
import br.com.medflow.exam.order.application.dto.request.UpdateExamRequestDto;
import br.com.medflow.exam.order.application.dto.request.UpdateExamStatusRequestDto;
import br.com.medflow.exam.order.application.dto.response.ExamOrderResponseDto;
import br.com.medflow.exam.order.application.dto.response.ExamResultResponseDto;
import br.com.medflow.exam.order.application.exception.ExamOrderNotFoundException;
import br.com.medflow.exam.order.application.exception.ExamOrderStatusTransitionException;
import br.com.medflow.exam.order.application.exception.ExamResultConflictException;
import br.com.medflow.exam.order.application.exception.ExamResultNotFoundException;
import br.com.medflow.exam.order.domain.ExamOrder;
import br.com.medflow.exam.order.domain.ExamResult;
import br.com.medflow.exam.order.domain.enums.ExamStatus;
import br.com.medflow.exam.order.domain.event.ExamLaboratoryUpdatedEvent;
import br.com.medflow.exam.order.domain.event.ExamOrderCreatedEvent;
import br.com.medflow.exam.order.persistence.ExamOrderRepository;
import br.com.medflow.exam.order.persistence.ExamResultRepository;
import br.com.medflow.messaging.outbox.domain.OutboxEvent;
import br.com.medflow.messaging.outbox.persistence.OutboxEventRepository;
import br.com.medflow.patient.application.exception.PatientNotFoundException;
import br.com.medflow.patient.persistence.PatientRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ExamOrderService {

    private final ExamOrderRepository examOrderRepository;
    private final PatientRepository patientRepository;
    private final ExamDefinitionRepository examDefinitionRepository;
    private final ExamResultRepository examResultRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public ExamOrderService(
            ExamOrderRepository examOrderRepository,
            PatientRepository patientRepository,
            ExamDefinitionRepository examDefinitionRepository,
            ExamResultRepository examResultRepository,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper) {
        this.examOrderRepository = examOrderRepository;
        this.patientRepository = patientRepository;
        this.examDefinitionRepository = examDefinitionRepository;
        this.examResultRepository = examResultRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ExamOrderResponseDto create(CreateExamRequestDto request) {
        // 1. Validações prévias
        validatePatientExists(request.patientId());
        ExamDefinition definition = findActiveDefinition(request.examCode());

        // 2. Criação e persistência do pedido no banco de dados
        ExamOrder examOrder = new ExamOrder(
                request.patientId(),
                definition.getCode(),
                request.priority()
        );
        ExamOrder savedExamOrder = examOrderRepository.save(examOrder);

        // 3. Registro do evento na tabela Outbox (na mesma transação)
        enqueueOrderCreatedEvent(savedExamOrder);

        // 4. Retorno para quem chamou a API
        return new ExamOrderResponseDto(savedExamOrder);
    }

    public ExamOrderResponseDto findById(UUID id) {
        ExamOrder examOrder = examOrderRepository.findById(id)
                .orElseThrow(() -> new ExamOrderNotFoundException(id));

        return new ExamOrderResponseDto(examOrder);
    }

    public Page<ExamOrderResponseDto> findAll(Pageable pageable) {
        return examOrderRepository.findAll(pageable)
                .map(ExamOrderResponseDto::new);
    }

    @Transactional
    public ExamOrderResponseDto update(UUID id, UpdateExamRequestDto request) {
        ExamOrder examOrder = examOrderRepository.findById(id)
                .orElseThrow(() -> new ExamOrderNotFoundException(id));

        if (examOrder.getStatus() != ExamStatus.RECEIVED) {
            throw new ExamOrderStatusTransitionException(id, examOrder.getStatus(), ExamStatus.RECEIVED);
        }

        validatePatientExists(request.patientId());
        ExamDefinition definition = findActiveDefinition(request.examCode());

        examOrder.update(
                request.patientId(),
                definition.getCode(),
                request.priority()
        );

        ExamOrder updatedExamOrder = examOrderRepository.save(examOrder);
        return new ExamOrderResponseDto(updatedExamOrder);
    }

    @Transactional
    public void delete(UUID id) {
        ExamOrder examOrder = examOrderRepository.findById(id)
                .orElseThrow(() -> new ExamOrderNotFoundException(id));

        if (examOrder.getStatus() != ExamStatus.RECEIVED) {
            throw new ExamOrderStatusTransitionException(id, examOrder.getStatus(), ExamStatus.CANCELLED);
        }

        examOrderRepository.delete(examOrder);
    }

    /**
     * Evolui a etapa do exame no laboratório e registra evento de notificação na Outbox.
     */
    @Transactional
    public ExamOrderResponseDto updateStatus(UUID id, UpdateExamStatusRequestDto request) {
        ExamOrder examOrder = examOrderRepository.findById(id)
                .orElseThrow(() -> new ExamOrderNotFoundException(id));

        examOrder.changeStatus(request.status());
        ExamOrder savedExamOrder = examOrderRepository.save(examOrder);
        enqueueLaboratoryUpdatedEvent(savedExamOrder);

        return new ExamOrderResponseDto(savedExamOrder);
    }

    /**
     * Registra o laudo clínico do paciente na tabela exam_results, marca a ordem como RELEASED
     * e grava evento na Outbox na mesma transação.
     */
    @Transactional
    public ExamResultResponseDto registerResult(UUID id, CreateExamResultRequestDto request) {
        ExamOrder examOrder = examOrderRepository.findById(id)
                .orElseThrow(() -> new ExamOrderNotFoundException(id));

        if (examOrder.isTerminalStatus() || examResultRepository.existsByOrderId(id)) {
            throw new ExamResultConflictException(id, "a ordem já possui resultado liberado ou foi encerrada");
        }

        ExamResult examResult = new ExamResult(
                examOrder.getId(),
                examOrder.getPatientId(),
                examOrder.getExamCode(),
                request.resultText()
        );
        ExamResult savedResult = examResultRepository.save(examResult);

        examOrder.markReleased();
        ExamOrder savedOrder = examOrderRepository.save(examOrder);
        enqueueLaboratoryUpdatedEvent(savedOrder);

        return new ExamResultResponseDto(savedResult);
    }

    public ExamResultResponseDto findResultByOrderId(UUID id) {
        ExamResult examResult = examResultRepository.findByOrderId(id)
                .orElseThrow(() -> new ExamResultNotFoundException(id));

        return new ExamResultResponseDto(examResult);
    }

    private void enqueueLaboratoryUpdatedEvent(ExamOrder examOrder) {
        ExamLaboratoryUpdatedEvent event = new ExamLaboratoryUpdatedEvent(examOrder);
        saveOutboxRecord(
                event.eventId(),
                "LABORATORY_UPDATE",
                examOrder.getId(),
                "ExamLaboratoryUpdatedEvent",
                event
        );
    }

    private void enqueueOrderCreatedEvent(ExamOrder examOrder) {
        ExamOrderCreatedEvent event = new ExamOrderCreatedEvent(examOrder);
        saveOutboxRecord(
                event.eventId(),
                "EXAM_ORDER",
                examOrder.getId(),
                "ExamOrderCreatedEvent",
                event
        );
    }

    private void saveOutboxRecord(
            UUID eventId,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            Object payloadObject) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(payloadObject);
            OutboxEvent outboxEvent = new OutboxEvent(
                    eventId,
                    aggregateType,
                    aggregateId,
                    eventType,
                    jsonPayload
            );
            outboxEventRepository.save(outboxEvent);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Erro ao converter evento de exame para JSON", e);
        }
    }

    private void validatePatientExists(UUID patientId) {
        if (!patientRepository.existsById(patientId)) {
            throw new PatientNotFoundException(patientId);
        }
    }

    private ExamDefinition findActiveDefinition(String examCode) {
        ExamDefinition definition = examDefinitionRepository
                .findByCodeIgnoreCase(examCode.trim())
                .orElseThrow(() -> new ExamDefinitionNotFoundException(examCode));

        if (!definition.isActive()) {
            throw new ExamDefinitionInactiveException(definition.getCode());
        }

        return definition;
    }
}
