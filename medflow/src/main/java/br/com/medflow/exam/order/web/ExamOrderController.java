package br.com.medflow.exam.order.web;

import br.com.medflow.exam.order.application.ExamOrderService;
import br.com.medflow.exam.order.application.dto.request.CreateExamRequestDto;
import br.com.medflow.exam.order.application.dto.request.CreateExamResultRequestDto;
import br.com.medflow.exam.order.application.dto.request.UpdateExamRequestDto;
import br.com.medflow.exam.order.application.dto.request.UpdateExamStatusRequestDto;
import br.com.medflow.exam.order.application.dto.response.ExamOrderResponseDto;
import br.com.medflow.exam.order.application.dto.response.ExamResultResponseDto;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/exam-orders")
public class ExamOrderController {

    private final ExamOrderService examOrderService;

    public ExamOrderController(ExamOrderService examOrderService) {
        this.examOrderService = examOrderService;
    }

    @GetMapping
    public ResponseEntity<Page<ExamOrderResponseDto>> findAll(
            @PageableDefault(size = 10, sort = "createdAt") Pageable pageable) {

        Page<ExamOrderResponseDto> response = examOrderService.findAll(pageable);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExamOrderResponseDto> findById(@PathVariable UUID id) {
        ExamOrderResponseDto response = examOrderService.findById(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<ExamOrderResponseDto> create(
            @Valid @RequestBody CreateExamRequestDto request) {

        ExamOrderResponseDto response = examOrderService.create(request);
        return ResponseEntity.status(201).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ExamOrderResponseDto> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateExamRequestDto request) {

        ExamOrderResponseDto response = examOrderService.update(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        examOrderService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ExamOrderResponseDto> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateExamStatusRequestDto request) {

        ExamOrderResponseDto response = examOrderService.updateStatus(id, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/result")
    public ResponseEntity<ExamResultResponseDto> registerResult(
            @PathVariable UUID id,
            @Valid @RequestBody CreateExamResultRequestDto request) {

        ExamResultResponseDto response = examOrderService.registerResult(id, request);
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping("/{id}/result")
    public ResponseEntity<ExamResultResponseDto> findResult(@PathVariable UUID id) {
        ExamResultResponseDto response = examOrderService.findResultByOrderId(id);
        return ResponseEntity.ok(response);
    }
}
