package br.com.medflow.exam.order.application;

import br.com.medflow.exam.order.application.dto.request.CreateExamRequestDto;
import br.com.medflow.exam.order.application.dto.response.ExamOrderResponseDto;
import br.com.medflow.exam.order.application.dto.request.UpdateExamRequestDto;
import br.com.medflow.exam.order.application.exception.ExamOrderNotFoundException;
import br.com.medflow.exam.order.domain.ExamOrder;
import br.com.medflow.exam.order.persistence.ExamOrderRepository;
import br.com.medflow.patient.application.exception.PatientNotFoundException;
import br.com.medflow.patient.persistence.PatientRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ExamOrderService {

    private final ExamOrderRepository examOrderRepository;
    private final PatientRepository patientRepository;

    public ExamOrderService(ExamOrderRepository examOrderRepository, PatientRepository patientRepository) {
        this.examOrderRepository = examOrderRepository;
        this.patientRepository = patientRepository;
    }

    public ExamOrderResponseDto create(CreateExamRequestDto request) {
        if (!patientRepository.existsById(request.patientId())) {
            throw new PatientNotFoundException(request.patientId());
        }

        ExamOrder examOrder = new ExamOrder(
                request.patientId(),
                request.examCode(),
                request.priority()
        );

        ExamOrder savedExamOrder = examOrderRepository.save(examOrder);
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

        examOrder.update(
                request.patientId(),
                request.examCode(),
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
}
