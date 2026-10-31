package br.com.medflow.exam.order.application;

import br.com.medflow.exam.order.application.dto.request.CreateExamRequestDto;
import br.com.medflow.exam.order.application.dto.response.ExamOrderResponseDto;
import br.com.medflow.exam.order.application.dto.request.UpdateExamRequestDto;
import br.com.medflow.exam.definition.application.exception.ExamDefinitionNotFoundException;
import br.com.medflow.exam.definition.application.exception.ExamDefinitionInactiveException;
import br.com.medflow.exam.definition.domain.ExamDefinition;
import br.com.medflow.exam.definition.domain.enums.SampleType;
import br.com.medflow.exam.definition.persistence.ExamDefinitionRepository;
import br.com.medflow.exam.order.application.exception.ExamOrderNotFoundException;
import br.com.medflow.exam.order.domain.ExamOrder;
import br.com.medflow.exam.order.domain.enums.ExamPriority;
import br.com.medflow.exam.order.persistence.ExamOrderRepository;
import br.com.medflow.patient.application.exception.PatientNotFoundException;
import br.com.medflow.patient.persistence.PatientRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.medflow.messaging.outbox.domain.OutboxEvent;
import br.com.medflow.messaging.outbox.domain.OutboxStatus;
import br.com.medflow.messaging.outbox.persistence.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.mockito.ArgumentCaptor;

class ExamOrderServiceTest {

    private final ExamOrderRepository repository = mock(ExamOrderRepository.class);
    private final PatientRepository patientRepository = mock(PatientRepository.class);
    private final ExamDefinitionRepository examDefinitionRepository = mock(ExamDefinitionRepository.class);
    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final ExamOrderService service = new ExamOrderService(
            repository,
            patientRepository,
            examDefinitionRepository,
            outboxEventRepository,
            objectMapper
    );

    @Test
    void deveCriarOrdemESalvarNoRepository() {
        UUID patientId = UUID.randomUUID();
        ExamDefinition definition = criarDefinicao();
        when(patientRepository.existsById(patientId)).thenReturn(true);
        when(examDefinitionRepository.findByCodeIgnoreCase("hemograma"))
                .thenReturn(Optional.of(definition));
        when(repository.save(any(ExamOrder.class)))
                .thenAnswer(invocation -> {
                    ExamOrder order = invocation.getArgument(0);
                    org.springframework.test.util.ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
                    return order;
                });

        CreateExamRequestDto request = new CreateExamRequestDto(
                patientId,
                " hemograma ",
                ExamPriority.NORMAL
        );

        ExamOrderResponseDto result = service.create(request);

        assertThat(result.patientId()).isEqualTo(patientId);
        assertThat(result.examCode()).isEqualTo("HEMOGRAMA");
        verify(repository).save(any(ExamOrder.class));

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        OutboxEvent savedOutbox = outboxCaptor.getValue();
        assertThat(savedOutbox.getAggregateType()).isEqualTo("EXAM_ORDER");
        assertThat(savedOutbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(savedOutbox.getPayload()).contains("HEMOGRAMA");
    }

    @Test
    void naoDeveCriarOrdemQuandoPacienteNaoExistir() {
        UUID patientId = UUID.randomUUID();
        CreateExamRequestDto request = new CreateExamRequestDto(
                patientId,
                "HEMOGRAMA",
                ExamPriority.NORMAL
        );
        when(patientRepository.existsById(patientId)).thenReturn(false);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(PatientNotFoundException.class);

        verify(repository, never()).save(any(ExamOrder.class));
    }

    @Test
    void naoDeveCriarOrdemQuandoDefinicaoNaoExistir() {
        UUID patientId = UUID.randomUUID();
        CreateExamRequestDto request = new CreateExamRequestDto(
                patientId,
                "INEXISTENTE",
                ExamPriority.NORMAL
        );
        when(patientRepository.existsById(patientId)).thenReturn(true);
        when(examDefinitionRepository.findByCodeIgnoreCase("INEXISTENTE"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ExamDefinitionNotFoundException.class);

        verify(repository, never()).save(any(ExamOrder.class));
    }

    @Test
    void naoDeveCriarOrdemQuandoDefinicaoEstiverInativa() {
        UUID patientId = UUID.randomUUID();
        ExamDefinition definition = criarDefinicao();
        definition.deactivate();
        CreateExamRequestDto request = new CreateExamRequestDto(
                patientId,
                "HEMOGRAMA",
                ExamPriority.NORMAL
        );
        when(patientRepository.existsById(patientId)).thenReturn(true);
        when(examDefinitionRepository.findByCodeIgnoreCase("HEMOGRAMA"))
                .thenReturn(Optional.of(definition));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ExamDefinitionInactiveException.class);

        verify(repository, never()).save(any(ExamOrder.class));
    }

    @Test
    void deveBuscarOrdemPorId() {
        UUID id = UUID.randomUUID();
        ExamOrder examOrder = new ExamOrder(UUID.randomUUID(), "GLICOSE", ExamPriority.NORMAL);
        when(repository.findById(id)).thenReturn(Optional.of(examOrder));

        ExamOrderResponseDto result = service.findById(id);

        assertThat(result.patientId()).isEqualTo(examOrder.getPatientId());
        assertThat(result.examCode()).isEqualTo("GLICOSE");
    }

    @Test
    void deveLancarExcecaoQuandoOrdemNaoExistir() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id))
                .isInstanceOf(ExamOrderNotFoundException.class);
    }

    @Test
    void deveListarOrdens() {
        List<ExamOrder> examOrders = List.of(
                new ExamOrder(UUID.randomUUID(), "HEMOGRAMA", ExamPriority.NORMAL)
        );
        PageRequest pageRequest = PageRequest.of(0, 10);
        Page<ExamOrder> page = new PageImpl<>(examOrders, pageRequest, 1);
        when(repository.findAll(pageRequest)).thenReturn(page);

        Page<ExamOrderResponseDto> result = service.findAll(pageRequest);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().examCode()).isEqualTo("HEMOGRAMA");
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getNumber()).isZero();
        assertThat(result.getSize()).isEqualTo(10);
    }

    @Test
    void deveAtualizarOrdem() {
        UUID id = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        ExamOrder examOrder = new ExamOrder(UUID.randomUUID(), "GLICOSE", ExamPriority.NORMAL);
        UpdateExamRequestDto request = new UpdateExamRequestDto(
                patientId,
                " hemograma ",
                ExamPriority.URGENT
        );
        when(repository.findById(id)).thenReturn(Optional.of(examOrder));
        when(patientRepository.existsById(patientId)).thenReturn(true);
        when(examDefinitionRepository.findByCodeIgnoreCase("hemograma"))
                .thenReturn(Optional.of(criarDefinicao()));
        when(repository.save(examOrder)).thenReturn(examOrder);

        ExamOrderResponseDto result = service.update(id, request);

        assertThat(result.patientId()).isEqualTo(patientId);
        assertThat(result.examCode()).isEqualTo("HEMOGRAMA");
        assertThat(result.priority()).isEqualTo(ExamPriority.URGENT);
        verify(repository).save(examOrder);
    }

    @Test
    void naoDeveAtualizarOrdemQuandoPacienteNaoExistir() {
        UUID id = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        ExamOrder examOrder = new ExamOrder(UUID.randomUUID(), "GLICOSE", ExamPriority.NORMAL);
        UpdateExamRequestDto request = new UpdateExamRequestDto(
                patientId,
                "HEMOGRAMA",
                ExamPriority.NORMAL
        );
        when(repository.findById(id)).thenReturn(Optional.of(examOrder));
        when(patientRepository.existsById(patientId)).thenReturn(false);

        assertThatThrownBy(() -> service.update(id, request))
                .isInstanceOf(PatientNotFoundException.class);

        verify(repository, never()).save(any(ExamOrder.class));
    }

    @Test
    void naoDeveAtualizarOrdemQuandoDefinicaoNaoExistir() {
        UUID id = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        ExamOrder examOrder = new ExamOrder(UUID.randomUUID(), "GLICOSE", ExamPriority.NORMAL);
        UpdateExamRequestDto request = new UpdateExamRequestDto(
                patientId,
                "INEXISTENTE",
                ExamPriority.NORMAL
        );
        when(repository.findById(id)).thenReturn(Optional.of(examOrder));
        when(patientRepository.existsById(patientId)).thenReturn(true);
        when(examDefinitionRepository.findByCodeIgnoreCase("INEXISTENTE"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, request))
                .isInstanceOf(ExamDefinitionNotFoundException.class);

        verify(repository, never()).save(any(ExamOrder.class));
    }

    @Test
    void naoDeveAtualizarOrdemQuandoDefinicaoEstiverInativa() {
        UUID id = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        ExamOrder examOrder = new ExamOrder(UUID.randomUUID(), "GLICOSE", ExamPriority.NORMAL);
        ExamDefinition definition = criarDefinicao();
        definition.deactivate();
        UpdateExamRequestDto request = new UpdateExamRequestDto(
                patientId,
                "HEMOGRAMA",
                ExamPriority.NORMAL
        );
        when(repository.findById(id)).thenReturn(Optional.of(examOrder));
        when(patientRepository.existsById(patientId)).thenReturn(true);
        when(examDefinitionRepository.findByCodeIgnoreCase("HEMOGRAMA"))
                .thenReturn(Optional.of(definition));

        assertThatThrownBy(() -> service.update(id, request))
                .isInstanceOf(ExamDefinitionInactiveException.class);

        verify(repository, never()).save(any(ExamOrder.class));
    }

    @Test
    void naoDeveAtualizarOrdemInexistente() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(
                id,
                new UpdateExamRequestDto(UUID.randomUUID(), "HEMOGRAMA", ExamPriority.NORMAL)
        )).isInstanceOf(ExamOrderNotFoundException.class);

        verify(repository, never()).save(any(ExamOrder.class));
    }

    @Test
    void deveExcluirOrdem() {
        UUID id = UUID.randomUUID();
        ExamOrder examOrder = new ExamOrder(UUID.randomUUID(), "GLICOSE", ExamPriority.NORMAL);
        when(repository.findById(id)).thenReturn(Optional.of(examOrder));

        service.delete(id);

        verify(repository).delete(examOrder);
    }

    @Test
    void naoDeveExcluirOrdemInexistente() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(ExamOrderNotFoundException.class);

        verify(repository, never()).delete(any(ExamOrder.class));
    }

    private ExamDefinition criarDefinicao() {
        return new ExamDefinition(
                "HEMOGRAMA",
                "Hemograma completo",
                SampleType.BLOOD
        );
    }
}
