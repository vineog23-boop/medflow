package br.com.medflow.exam.definition.application.exception;

import java.util.UUID;

public class ExamDefinitionNotFoundException extends RuntimeException {

    public ExamDefinitionNotFoundException(UUID id) {
        super("Definição de exame não encontrada: " + id);
    }

    public ExamDefinitionNotFoundException(String code) {
        super("Definição de exame não encontrada: " + code);
    }
}
