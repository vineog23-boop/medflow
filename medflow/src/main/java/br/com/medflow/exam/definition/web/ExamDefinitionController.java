package br.com.medflow.exam.definition.web;

import br.com.medflow.exam.definition.application.ExamDefinitionService;
import br.com.medflow.exam.definition.application.dto.request.CreateExamDefinitionRequestDto;
import br.com.medflow.exam.definition.application.dto.request.UpdateExamDefinitionRequestDto;
import br.com.medflow.exam.definition.application.dto.response.ExamDefinitionResponseDto;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/exam-definitions")
public class ExamDefinitionController {

    private final ExamDefinitionService examDefinitionService;

    public ExamDefinitionController(ExamDefinitionService examDefinitionService) {
        this.examDefinitionService = examDefinitionService;
    }

    @GetMapping
    public ResponseEntity<Page<ExamDefinitionResponseDto>> findAll(
            @PageableDefault(size = 10, sort = "createdAt") Pageable pageable) {

        Page<ExamDefinitionResponseDto> response = examDefinitionService.findAll(pageable);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExamDefinitionResponseDto> findById(@PathVariable UUID id) {
        ExamDefinitionResponseDto response = examDefinitionService.findById(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<ExamDefinitionResponseDto> create(
            @Valid @RequestBody CreateExamDefinitionRequestDto request) {

        ExamDefinitionResponseDto response = examDefinitionService.create(request);
        return ResponseEntity.status(201).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ExamDefinitionResponseDto> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateExamDefinitionRequestDto request) {

        ExamDefinitionResponseDto response = examDefinitionService.update(id, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/deactivation")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        examDefinitionService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
