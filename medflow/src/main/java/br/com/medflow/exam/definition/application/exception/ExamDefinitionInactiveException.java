package br.com.medflow.exam.definition.application.exception;

public class ExamDefinitionInactiveException extends RuntimeException {

    public ExamDefinitionInactiveException(String code) {
        super("Definição de exame inativa: " + code);
    }
}
