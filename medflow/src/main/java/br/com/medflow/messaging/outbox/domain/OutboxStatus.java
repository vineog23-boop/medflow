package br.com.medflow.messaging.outbox.domain;

public enum OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
