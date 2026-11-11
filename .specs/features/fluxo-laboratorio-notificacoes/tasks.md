# Tasks

## Test Coverage Matrix

| Requirement | Layer | Test evidence planned |
| --- | --- | --- |
| FLOW-01 | Unit + Integration | `LaboratoryEventListenerTest` (Mockito) e `ExamOrderMessagingIntegrationTest` (`RECEIVED` -> `AWAITING_COLLECTION`) |
| FLOW-02 | Unit + Web | `ExamOrderTest`, `ExamOrderServiceTest`, `ExamOrderControllerTest` e `GlobalExceptionHandlerTest` para `PATCH /exam-orders/{id}/status` (`200`, `404`, `409`, `422`) |
| FLOW-03 | Unit + Web | `ExamOrderServiceTest`, `ExamOrderControllerTest` e `GlobalExceptionHandlerTest` para `POST /exam-orders/{id}/result` (`201`, `409`) e `GET /exam-orders/{id}/result` (`200`, `404`) |
| FLOW-04 | Unit + Web + Integration | `NotificationEventListenerTest`, `NotificationControllerTest` e `ExamOrderMessagingIntegrationTest` validando gravação na tabela `notifications` via Kafka e consulta em `GET /notifications` |

## Gate Check Commands

- **Quick**: `./mvnw -Dtest=<classe> test`
- **Full**: `./mvnw test`
- **Build**: `./mvnw test`

## Execution Plan

### Phase 1 - Schema, Domínio, Evolução do Exame e Entrega do Laudo (`ExamResult`)

```text
T1 -> T2
```

### T1: Criar migration V4, entidades ExamResult e Notification e regras de estado na ExamOrder

**Depends on**: none
**Where**: `V4__create_exam_results_and_notifications_tables.sql`
**What**: Criar a migration Flyway `V4` (`exam_results` e `notifications`), adicionar os métodos de transição com invariantes na `ExamOrder` (`acceptByLaboratory`, `changeStatus`, `markReleased`), criar as entidades `ExamResult` e `Notification`, seus repositórios, as novas exceções (`ExamOrderStatusTransitionException`, `ExamResultNotFoundException`, `ExamResultConflictException`) e o evento `ExamLaboratoryUpdatedEvent`.
**Tests**: Testes unitários de domínio em `ExamOrderTest`.
**Gate**: Quick
**Done when**: Migration `V4`, entidades, exceções e regras de transição compilam e todos os testes de domínio passam.

### T2: Implementar evolução de status, entrega/consulta de ExamResult e tratamento RFC 9457

**Depends on**: T1
**Where**: `ExamOrderService.java`
**What**: Implementar `updateStatus(UUID id, UpdateExamStatusRequestDto request)`, `registerResult(UUID id, CreateExamResultRequestDto request)` e `findResultByOrderId(UUID id)` na `ExamOrderService`, expondo `PATCH /exam-orders/{id}/status`, `POST /exam-orders/{id}/result` e `GET /exam-orders/{id}/result` no `ExamOrderController`, além de mapear os novos erros `404` e `409` na `GlobalExceptionHandler`.
**Tests**: Testes unitários em `ExamOrderServiceTest`, `ExamOrderControllerTest` e testes `MockMvc` em `GlobalExceptionHandlerTest`.
**Gate**: Quick
**Done when**: FLOW-02 e FLOW-03 possuem cobertura unitária e web completa para evoluções de status, entrega/leitura do laudo `ExamResult` e erros `404`/`409`/`422`.

### Phase 2 - Mensageria Kafka (Médico -> Laboratório e Laboratório -> Paciente)

```text
T3 -> T4
```

### T3: Implementar LaboratoryEventListener e roteamento para exam-notifications.v1 no OutboxPublisher

**Depends on**: T2
**Where**: `LaboratoryEventListener.java`
**What**: Criar `LaboratoryEventListener` (`groupId = "laboratory-group"`) escutando `exam-orders.v1` para aceitar solicitações mudando `RECEIVED` para `AWAITING_COLLECTION` com idempotência em `processed_events`, e atualizar `OutboxPublisher` para rotear `ExamLaboratoryUpdatedEvent` para o tópico `exam-notifications.v1`.
**Tests**: Testes unitários em `LaboratoryEventListenerTest` e `OutboxPublisherTest`.
**Gate**: Quick
**Done when**: FLOW-01 aceita pedidos via Kafka sem duplicidade e o `OutboxPublisher` roteia ambos os tópicos corretamente.

### T4: Persistir Notification no NotificationEventListener, expor GET /notifications e provar fluxo E2E

**Depends on**: T3
**Where**: `NotificationEventListener.java`
**What**: Atualizar `NotificationEventListener` (`groupId = "notification-group"`) para escutar `exam-notifications.v1`, salvar registros reais na tabela `notifications` com idempotência em `processed_events`, criar `NotificationService` + `NotificationController` (`GET /notifications` paginado com filtro opcional `patientId`) e atualizar o teste E2E em `ExamOrderMessagingIntegrationTest`.
**Tests**: Testes unitários em `NotificationEventListenerTest`, `NotificationControllerTest` e teste de integração E2E com `Testcontainers` (`PostgreSQL` + `Kafka`) em `ExamOrderMessagingIntegrationTest`.
**Gate**: Full
**Done when**: FLOW-04 persiste e expõe notificações reais via Kafka e a suíte completa (`./mvnw test`) passa sem falhas.

## Task Breakdown

- [x] T1 - Criar migration V4, entidades ExamResult e Notification e regras de estado na ExamOrder. Gate: 15 testes, 0 falhas.
- [x] T2 - Implementar evolução de status, entrega/consulta de ExamResult e tratamento RFC 9457. Gate: 37 testes, 0 falhas.
- [x] T3 - Implementar LaboratoryEventListener e roteamento para exam-notifications.v1 no OutboxPublisher. Gate: 5 testes, 0 falhas.
- [x] T4 - Persistir Notification no NotificationEventListener, expor GET /notifications e provar fluxo E2E. Gate: 119 testes, 0 falhas.
