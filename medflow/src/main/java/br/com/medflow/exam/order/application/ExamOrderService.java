package br.com.medflow.exam.order.application;

import br.com.medflow.exam.definition.application.exception.ExamDefinitionInactiveException;
import br.com.medflow.exam.definition.application.exception.ExamDefinitionNotFoundException;
import br.com.medflow.exam.definition.domain.ExamDefinition;
import br.com.medflow.exam.definition.persistence.ExamDefinitionRepository;
import br.com.medflow.exam.order.application.dto.request.CreateExamRequestDto;
import br.com.medflow.exam.order.application.dto.request.UpdateExamRequestDto;
import br.com.medflow.exam.order.application.dto.response.ExamOrderResponseDto;
import br.com.medflow.exam.order.application.exception.ExamOrderNotFoundException;
import br.com.medflow.exam.order.domain.ExamOrder;
import br.com.medflow.exam.order.domain.event.ExamOrderCreatedEvent;
import br.com.medflow.exam.order.persistence.ExamOrderRepository;
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
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public ExamOrderService(
            ExamOrderRepository examOrderRepository,
            PatientRepository patientRepository,
            ExamDefinitionRepository examDefinitionRepository,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper) {
        this.examOrderRepository = examOrderRepository;
        this.patientRepository = patientRepository;
        this.examDefinitionRepository = examDefinitionRepository;
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
        salvarEventoOutbox(savedExamOrder);

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

    public ExamOrderResponseDto update(UUID id, UpdateExamRequestDto request) {
        ExamOrder examOrder = examOrderRepository.findById(id)
                .orElseThrow(() -> new ExamOrderNotFoundException(id));

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

    public void delete(UUID id) {
        ExamOrder examOrder = examOrderRepository.findById(id)
                .orElseThrow(() -> new ExamOrderNotFoundException(id));

        examOrderRepository.delete(examOrder);
    }

    private void salvarEventoOutbox(ExamOrder order) {
        // Cria o evento a partir do pedido salvo, igual você faz no new ExamOrderResponseDto(order)
        ExamOrderCreatedEvent event = new ExamOrderCreatedEvent(order);

        try {
            // Converte o evento para JSON
            String jsonPayload = objectMapper.writeValueAsString(event);

            // Cria o registro da tabela outbox e salva
            OutboxEvent outboxEvent = new OutboxEvent(
                    event.eventId(),
                    "EXAM_ORDER",
                    order.getId(),
                    "ExamOrderCreatedEvent",
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
