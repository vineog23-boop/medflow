# Fluxo Laboratório e Notificações Validation

**Date**: 2026-11-11
**Spec**: `.specs/features/fluxo-laboratorio-notificacoes/spec.md`
**Diff range**: `2c705e7..28e8e13` (Round 2 após correção dos gaps do Round 1)
**Verifier**: independent sub-agent (author ≠ verifier)

## Validation: PASS

---

## Task Completion

| Task | Status | Notes |
| ---- | ------ | ----- |
| T1 | ✅ Done | Migration `V4`, `ExamResult`, `Notification`, exceções, `ExamLaboratoryUpdatedEvent` e transições em `ExamOrder` implementados e cobertos em `ExamOrderTest` (inclui `RECEIVED`, `REJECTED`, `CANCELLED`). |
| T2 | ✅ Done | `PATCH /status`, `POST /result`, `GET /result`, `ExamOrderService` e `GlobalExceptionHandler` implementados; todos os fluxos de sucesso, `404`, `409` e `422` cobertos em `ExamOrderServiceTest`, `ExamOrderControllerTest` e `GlobalExceptionHandlerTest`. |
| T3 | ✅ Done | `LaboratoryEventListener` (`laboratory-group`) e roteamento de `ExamLaboratoryUpdatedEvent` para `exam-notifications.v1` no `OutboxPublisher` implementados e verificados. |
| T4 | ✅ Done | `NotificationEventListener` (`notification-group`), `NotificationService` e `GET /notifications` implementados com testes em `NotificationEventListenerTest`, `NotificationServiceTest`, `NotificationControllerTest` (incluindo `createdAt DESC`) e `ExamOrderMessagingIntegrationTest`. |

---

## Spec-Anchored Acceptance Criteria

### P1 (FLOW-01): Aceitar solicitação médica no Laboratório via Kafka

| Criterion (WHEN X THEN Y) | Spec-defined outcome | `file:line` + assertion | Result |
| ------------------------- | -------------------- | ----------------------- | ------ |
| AC-1: WHEN o médico solicita um exame ativo em `POST /exam-orders` THEN salva `ExamOrder` (`RECEIVED`) e grava `OutboxEvent` (`PENDING`) para `exam-orders.v1` | `ExamOrder.status = RECEIVED`, `OutboxEvent.status = PENDING` (`ExamOrderCreatedEvent`), roteado para `exam-orders.v1` | `medflow/src/test/java/br/com/medflow/exam/order/domain/ExamOrderTest.java:33` - `assertThat(examOrder.getStatus()).isEqualTo(ExamStatus.RECEIVED);`<br>`medflow/src/test/java/br/com/medflow/exam/order/application/ExamOrderServiceTest.java:96-98` - `assertThat(savedOutbox.getAggregateType()).isEqualTo("EXAM_ORDER");`, `assertThat(savedOutbox.getStatus()).isEqualTo(OutboxStatus.PENDING);`, `assertThat(savedOutbox.getPayload()).contains("HEMOGRAMA");`<br>`medflow/src/test/java/br/com/medflow/messaging/ExamOrderMessagingIntegrationTest.java:104,106-110` - `assertThat(response.status()).isEqualTo(ExamStatus.RECEIVED);` e `"ExamOrderCreatedEvent".equals(e.getEventType())`<br>`medflow/src/test/java/br/com/medflow/messaging/outbox/publisher/OutboxPublisherTest.java:64-65` - `verify(kafkaTemplate).send("exam-orders.v1", orderId.toString(), payload);`, `assertThat(pendingEvent.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);` | ✅ PASS |
| AC-2: WHEN `LaboratoryEventListener` (`laboratory-group`) consome `ExamOrderCreatedEvent` inédito em `RECEIVED` THEN atualiza para `AWAITING_COLLECTION` e registra `ProcessedEvent` para `laboratory-group` | `ExamOrder.status = AWAITING_COLLECTION` e `ProcessedEvent(eventId, "laboratory-group")` persistido | `medflow/src/test/java/br/com/medflow/exam/order/domain/ExamOrderTest.java:136-137` - `assertThat(changed).isTrue();`, `assertThat(examOrder.getStatus()).isEqualTo(ExamStatus.AWAITING_COLLECTION);`<br>`medflow/src/test/java/br/com/medflow/laboratory/application/LaboratoryEventListenerTest.java:59-61` - `assertThat(examOrder.getStatus()).isEqualTo(ExamStatus.AWAITING_COLLECTION);`, `verify(examOrderRepository).save(examOrder);`, `verify(processedEventRepository).save(any(ProcessedEvent.class));`<br>`medflow/src/test/java/br/com/medflow/messaging/ExamOrderMessagingIntegrationTest.java:116-119` - `assertThat(processedEventRepository.existsByEventIdAndConsumerGroup(createdOutbox.getId(), "laboratory-group")).isTrue();`, `assertThat(acceptedOrder.getStatus()).isEqualTo(ExamStatus.AWAITING_COLLECTION);` | ✅ PASS |
| AC-3: IF `ExamOrderCreatedEvent` já registrado em `processed_events` para `laboratory-group` THEN descarta sem alterar `ExamOrder` | Listener descarta sem buscar/alterar `ExamOrder` nem gravar `ProcessedEvent` | `medflow/src/test/java/br/com/medflow/laboratory/application/LaboratoryEventListenerTest.java:82-83` - `verify(examOrderRepository, never()).findById(any());`, `verify(processedEventRepository, never()).save(any());` | ✅ PASS |
| AC-4: WHILE `ExamOrder` em status ≠ `RECEIVED` WHEN `ExamOrderCreatedEvent` atrasado chega THEN mantém status atual sem retroceder | `acceptByLaboratory()` retorna `false`, mantém status atual, não chama `examOrderRepository.save` | `medflow/src/test/java/br/com/medflow/exam/order/domain/ExamOrderTest.java:147-148` - `assertThat(changed).isFalse();`, `assertThat(examOrder.getStatus()).isEqualTo(ExamStatus.COLLECTED);`<br>`medflow/src/test/java/br/com/medflow/laboratory/application/LaboratoryEventListenerTest.java:109-111` - `assertThat(examOrder.getStatus()).isEqualTo(ExamStatus.COLLECTED);`, `verify(examOrderRepository, never()).save(any());`, `verify(processedEventRepository).save(any(ProcessedEvent.class));` | ✅ PASS |

### P1 (FLOW-02): Evoluir etapas do exame pelo Laboratório (`PATCH /status`)

| Criterion (WHEN X THEN Y) | Spec-defined outcome | `file:line` + assertion | Result |
| ------------------------- | -------------------- | ----------------------- | ------ |
| AC-5: WHEN `PATCH /exam-orders/{id}/status` com `ExamStatus` operacional válido (exceto `RECEIVED` e `RELEASED`) para pedido não finalizado THEN atualiza status, grava `OutboxEvent` (`ExamLaboratoryUpdatedEvent`) e responde `200 OK` com `ExamOrderResponseDto` | `200 OK` com `ExamOrderResponseDto`, status atualizado, `OutboxEvent` (`ExamLaboratoryUpdatedEvent`) gravado; `RECEIVED` rejeitado | `medflow/src/test/java/br/com/medflow/exam/order/web/ExamOrderControllerTest.java:85-87` - `.andExpect(status().isOk())`, `.andExpect(jsonPath("$.id").value(ORDER_ID.toString()))`, `.andExpect(jsonPath("$.status").value("COLLECTED"))`<br>`medflow/src/test/java/br/com/medflow/exam/order/application/ExamOrderServiceTest.java:326,330-332` - `assertThat(result.status()).isEqualTo(ExamStatus.COLLECTED);`, `assertThat(savedOutbox.getEventType()).isEqualTo("ExamLaboratoryUpdatedEvent");`, `assertThat(savedOutbox.getPayload()).contains("COLLECTED");`<br>`medflow/src/test/java/br/com/medflow/exam/order/domain/ExamOrderTest.java:173-174` - `assertThatThrownBy(() -> examOrder.changeStatus(ExamStatus.RECEIVED)).isInstanceOf(ExamOrderStatusTransitionException.class);` | ✅ PASS |
| AC-6: IF `PATCH /exam-orders/{id}/status` com `status = RELEASED` sem laudo ou em pedido finalizado (`RELEASED`, `REJECTED`, `CANCELLED`) THEN `409 Conflict` (`urn:medflow:problem:exam-order-status-transition-invalid`) | `409 Conflict` com `type = urn:medflow:problem:exam-order-status-transition-invalid` para `RELEASED` alvo e ordens `RELEASED`, `REJECTED` e `CANCELLED` | `medflow/src/test/java/br/com/medflow/exam/order/domain/ExamOrderTest.java:158-165,182-189` - `changeStatus(ExamStatus.RELEASED)` e `changeStatus(...)` a partir de `RELEASED`, `REJECTED` e `CANCELLED` lançam `ExamOrderStatusTransitionException`<br>`medflow/src/test/java/br/com/medflow/exam/order/application/ExamOrderServiceTest.java:342-343,368-369` - `assertThatThrownBy(() -> service.updateStatus(..., RELEASED/CANCELLED)).isInstanceOf(ExamOrderStatusTransitionException.class);`<br>`medflow/src/test/java/br/com/medflow/exam/order/web/ExamOrderControllerTest.java:186-187` - `.andExpect(status().isConflict())`, `.andExpect(jsonPath("$.type").value("urn:medflow:problem:exam-order-status-transition-invalid"))`<br>`medflow/src/test/java/br/com/medflow/shared/web/error/GlobalExceptionHandlerTest.java:139-144` - `.andExpect(status().isConflict())`, `.andExpect(jsonPath("$.type").value("urn:medflow:problem:exam-order-status-transition-invalid"))`, `.andExpect(jsonPath("$.status").value(409))` | ✅ PASS |
| AC-7: IF o pedido informado em `PATCH /exam-orders/{id}/status` não existir THEN `404 Not Found` (`urn:medflow:problem:exam-order-not-found`) | `404 Not Found` com `type = urn:medflow:problem:exam-order-not-found` | `medflow/src/test/java/br/com/medflow/exam/order/application/ExamOrderServiceTest.java:354-357` - `assertThatThrownBy(() -> service.updateStatus(id, ...)).isInstanceOf(ExamOrderNotFoundException.class);`, `verify(repository, never()).save(any(ExamOrder.class));`<br>`medflow/src/test/java/br/com/medflow/exam/order/web/ExamOrderControllerTest.java:168-169` - `.andExpect(status().isNotFound())`, `.andExpect(jsonPath("$.type").value("urn:medflow:problem:exam-order-not-found"))`<br>`medflow/src/test/java/br/com/medflow/shared/web/error/GlobalExceptionHandlerTest.java:79-84` - `.andExpect(status().isNotFound())`, `.andExpect(jsonPath("$.type").value("urn:medflow:problem:exam-order-not-found"))` | ✅ PASS |
| AC-8: IF corpo de `PATCH /exam-orders/{id}/status` contiver `status` nulo THEN `422 Unprocessable Content` com JSON Pointer `#/status` | `422 Unprocessable Content` e `errors[0].pointer = "#/status"` | `medflow/src/test/java/br/com/medflow/exam/order/web/ExamOrderControllerTest.java:101-103` - `.andExpect(status().isUnprocessableContent())`, `.andExpect(jsonPath("$.type").value("urn:medflow:problem:validation-error"))`, `.andExpect(jsonPath("$.errors[0].pointer").value("#/status"))` | ✅ PASS |

### P1 (FLOW-03): Entregar o resultado do exame (`ExamResult`) para o Paciente

| Criterion (WHEN X THEN Y) | Spec-defined outcome | `file:line` + assertion | Result |
| ------------------------- | -------------------- | ----------------------- | ------ |
| AC-9: WHEN `POST /exam-orders/{id}/result` com `resultText` válido para pedido elegível THEN persiste `ExamResult`, muda `ExamOrder` para `RELEASED`, grava `ExamLaboratoryUpdatedEvent` na Outbox e responde `201 Created` | `ExamResult` salvo em `exam_results`, `ExamOrder.status = RELEASED`, `OutboxEvent` (`ExamLaboratoryUpdatedEvent`), HTTP `201 Created` com `ExamResultResponseDto` | `medflow/src/test/java/br/com/medflow/exam/order/web/ExamOrderControllerTest.java:128-130` - `.andExpect(status().isCreated())`, `.andExpect(jsonPath("$.orderId").value(ORDER_ID.toString()))`, `.andExpect(jsonPath("$.resultText").value("Sem alterações clínicas."))`<br>`medflow/src/test/java/br/com/medflow/exam/order/application/ExamOrderServiceTest.java:391-398` - `assertThat(result.orderId()).isEqualTo(id);`, `assertThat(result.resultText()).isEqualTo("Hemácias normais");`, `assertThat(examOrder.getStatus()).isEqualTo(ExamStatus.RELEASED);`, `assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("ExamLaboratoryUpdatedEvent");`, `assertThat(outboxCaptor.getValue().getPayload()).contains("RELEASED");`<br>`medflow/src/test/java/br/com/medflow/messaging/ExamOrderMessagingIntegrationTest.java:129-143` - persiste `ExamResult` no PostgreSQL e grava `ExamLaboratoryUpdatedEvent` na `outbox_events` | ✅ PASS |
| AC-10: IF tentar segundo `POST /exam-orders/{id}/result` para pedido com `ExamResult` ou `CANCELLED`/`REJECTED` THEN `409 Conflict` (`urn:medflow:problem:exam-result-conflict`) | `409 Conflict` (`urn:medflow:problem:exam-result-conflict`) para duplicidade OU estado `CANCELLED`/`REJECTED` | `medflow/src/test/java/br/com/medflow/exam/order/application/ExamOrderServiceTest.java:409-412,424-437` - testa `existsByOrderId(id) == true` e também ordens `CANCELLED` e `REJECTED` com `existsByOrderId == false` lançando `ExamResultConflictException` e `verify(examResultRepository, never()).save(any(ExamResult.class));`<br>`medflow/src/test/java/br/com/medflow/exam/order/web/ExamOrderControllerTest.java:203-204` - `.andExpect(status().isConflict())`, `.andExpect(jsonPath("$.type").value("urn:medflow:problem:exam-result-conflict"))`<br>`medflow/src/test/java/br/com/medflow/shared/web/error/GlobalExceptionHandlerTest.java:165-170` - `.andExpect(status().isConflict())`, `.andExpect(jsonPath("$.type").value("urn:medflow:problem:exam-result-conflict"))` | ✅ PASS |
| AC-11: WHEN `GET /exam-orders/{id}/result` para pedido com resultado liberado THEN `200 OK` com `ExamResultResponseDto` | `200 OK` com `ExamResultResponseDto` | `medflow/src/test/java/br/com/medflow/exam/order/web/ExamOrderControllerTest.java:148-150` - `.andExpect(status().isOk())`, `.andExpect(jsonPath("$.orderId").value(ORDER_ID.toString()))`, `.andExpect(jsonPath("$.resultText").value("Hemograma dentro da normalidade."))`<br>`medflow/src/test/java/br/com/medflow/exam/order/application/ExamOrderServiceTest.java:448-449` - `assertThat(response.orderId()).isEqualTo(orderId);`, `assertThat(response.resultText()).isEqualTo("Valores normais");` | ✅ PASS |
| AC-12: IF `GET /exam-orders/{id}/result` para pedido sem resultado liberado THEN `404 Not Found` (`urn:medflow:problem:exam-result-not-found`) | `404 Not Found` (`urn:medflow:problem:exam-result-not-found`) | `medflow/src/test/java/br/com/medflow/exam/order/application/ExamOrderServiceTest.java:457-458` - `assertThatThrownBy(() -> service.findResultByOrderId(orderId)).isInstanceOf(ExamResultNotFoundException.class);`<br>`medflow/src/test/java/br/com/medflow/exam/order/web/ExamOrderControllerTest.java:213-214` - `.andExpect(status().isNotFound())`, `.andExpect(jsonPath("$.type").value("urn:medflow:problem:exam-result-not-found"))`<br>`medflow/src/test/java/br/com/medflow/shared/web/error/GlobalExceptionHandlerTest.java:152-157` - `.andExpect(status().isNotFound())`, `.andExpect(jsonPath("$.type").value("urn:medflow:problem:exam-result-not-found"))`, `.andExpect(jsonPath("$.status").value(404))` | ✅ PASS |

### P1 (FLOW-04): Notificar o Paciente via Kafka e expor consulta de avisos

| Criterion (WHEN X THEN Y) | Spec-defined outcome | `file:line` + assertion | Result |
| ------------------------- | -------------------- | ----------------------- | ------ |
| AC-13: WHEN `NotificationEventListener` (`notification-group`) consome `ExamLaboratoryUpdatedEvent` inédito THEN salva `Notification` (`orderId`, `patientId`, `examCode`, `status`, `message`, `createdAt`) e grava `ProcessedEvent` | `Notification` persistida em `notifications` com todos os campos e `ProcessedEvent(eventId, "notification-group")` | `medflow/src/test/java/br/com/medflow/notification/application/NotificationEventListenerTest.java:64-69` - asserta `getOrderId()`, `getPatientId()`, `getExamCode()`, `getStatus()`, `getMessage().contains("liberado")` e `verify(processedEventRepository).save(any(ProcessedEvent.class))`<br>`medflow/src/test/java/br/com/medflow/messaging/ExamOrderMessagingIntegrationTest.java:151-160` - `assertThat(processedEventRepository.existsByEventIdAndConsumerGroup(labEvent.getId(), "notification-group")).isTrue();`, `assertThat(notifications.getTotalElements()).isEqualTo(2);` | ✅ PASS |
| AC-14: IF `NotificationEventListener` receber `eventId` já em `processed_events` para `notification-group` THEN ignora duplicata sem inserir nova linha | Zero chamadas a `notificationRepository.save` e contagem inalterada no banco | `medflow/src/test/java/br/com/medflow/notification/application/NotificationEventListenerTest.java:90-91` - `verify(notificationRepository, never()).save(any(Notification.class));`, `verify(processedEventRepository, never()).save(any(ProcessedEvent.class));`<br>`medflow/src/test/java/br/com/medflow/messaging/ExamOrderMessagingIntegrationTest.java:170-171` - `assertThat(notificationRepository.count()).isEqualTo(notifCountBefore);`, `assertThat(processedEventRepository.count()).isEqualTo(processedCountBefore);` | ✅ PASS |
| AC-15: WHEN chama `GET /notifications` (com filtro opcional `?patientId={uuid}`) THEN `200 OK` com `Page<NotificationResponseDto>` ordenado do mais recente para o mais antigo | `200 OK` com `Page<NotificationResponseDto>` ordenado por `createdAt DESC` | `medflow/src/test/java/br/com/medflow/notification/web/NotificationControllerTest.java:59-67,78-85` - asserta `status().isOk()`, payload JSON, captura `Pageable` e valida `order.getDirection()` igual a `Sort.Direction.DESC` para `createdAt` (com e sem `patientId`)<br>`medflow/src/test/java/br/com/medflow/notification/application/NotificationServiceTest.java:45-49,70-74` - valida delegação para `findAll(pageable)` e `findByPatientId(patientId, pageable)`<br>`medflow/src/test/java/br/com/medflow/messaging/ExamOrderMessagingIntegrationTest.java:154-160` - verifica `RELEASED` no índice 0 e `COLLECTED` no índice 1 | ✅ PASS |

**Status**: ✅ All ACs covered (15/15 PASS; todas as 5 lacunas do Round 1 fechadas)

---

## Discrimination Sensor

Executado em worktree temporário isolado (`/tmp/medflow-verifier-wt` em `28e8e13`), revertendo cada mutante via `git checkout -- .` após cada teste e removendo o worktree ao final (`git status --porcelain` idêntico ao baseline antes e depois).

| Mutation | File:line | Description | Killed? |
| -------- | --------- | ----------- | ------- |
| 1 | `medflow/src/main/java/br/com/medflow/notification/web/NotificationController.java:30` | Invertida ordenação padrão `Sort.Direction.DESC` → `Sort.Direction.ASC` em `@PageableDefault` (re-teste do sobrevivente do Round 1) | ✅ Killed (`NotificationControllerTest:67,85`; 2/4 falhas) |
| 2 | `medflow/src/main/java/br/com/medflow/exam/order/application/ExamOrderService.java:141` | Removida guarda `examOrder.isTerminalStatus() \|\|` em `registerResult` (re-teste do sobrevivente do Round 1) | ✅ Killed (`ExamOrderServiceTest:425`; 1/23 falhas) |
| 3 | `medflow/src/main/java/br/com/medflow/exam/order/domain/ExamOrder.java:118` | Permitido `ExamStatus.RECEIVED` em `changeStatus` (`targetStatus == ExamStatus.RECEIVED \|\| targetStatus == ExamStatus.RELEASED` → `targetStatus == ExamStatus.RELEASED`) | ✅ Killed (`ExamOrderTest:173`; 1/13 falhas) |
| 4 | `medflow/src/main/java/br/com/medflow/exam/order/application/ExamOrderService.java:127` | Removida chamada `salvarEventoLaboratorioOutbox(savedExamOrder)` em `updateStatus` | ✅ Killed (`ExamOrderServiceTest:328`; 1/23 falhas) |
| 5 | `medflow/src/main/java/br/com/medflow/laboratory/application/LaboratoryEventListener.java:46-49` | Removida guarda de idempotência `if (processedEventRepository.existsByEventIdAndConsumerGroup(...)) return;` | ✅ Killed (`LaboratoryEventListenerTest:80`; 1/3 erros) |
| 6 | `medflow/src/main/java/br/com/medflow/messaging/outbox/publisher/OutboxPublisher.java:102` | Retornado `examOrdersTopic` no lugar de `examNotificationsTopic` em `resolveTopic` | ✅ Killed (`OutboxPublisherTest:94`; 1/2 falhas) |
| 7 | `medflow/src/main/java/br/com/medflow/notification/application/NotificationEventListener.java:49-53` | Removida guarda de idempotência `if (processedEventRepository.existsByEventIdAndConsumerGroup(...)) return;` | ✅ Killed (`NotificationEventListenerTest:90`; 1/2 falhas) |
| 8 | `medflow/src/main/java/br/com/medflow/exam/order/domain/ExamOrder.java:99` | Removida proteção contra evento atrasado em `acceptByLaboratory()` (`this.status != ExamStatus.RECEIVED` → `false`) | ✅ Killed (`ExamOrderTest:147`, `LaboratoryEventListenerTest:109`; 2/16 falhas) |

**Sensor depth**: P0-full (8 mutações direcionadas cobrindo web, service, domain, outbox e ambos os listeners)
**Result**: 8/8 killed (0 survived) - PASS ✅

---

## Code Quality

| Principle | Status |
| --------- | ------ |
| Minimum code | ✅ |
| Surgical changes | ✅ |
| No scope creep | ✅ |
| Matches patterns | ✅ |
| Spec-anchored outcome check (asserted values match spec) | ✅ |
| Per-layer Coverage Expectation met (domain 1:1 ACs; routes happy+edge+error) | ✅ |
| Every test maps to a spec requirement - no unclaimed tests | ✅ |
| Documented guidelines followed: `/Users/Vini/conf-agents/estilos/java-spring.md`, `/Users/Vini/.gemini/config/skills/tlc-spec-driven/references/coding-principles.md` | ✅ |

---

## Edge Cases

- [x] Mensagem idêntica entregue duas vezes para `laboratory-group` (`exam-orders.v1`) ou `notification-group` (`exam-notifications.v1`) descartada consultando `processed_events(event_id, consumer_group)` (`LaboratoryEventListenerTest.java:77-84`, `NotificationEventListenerTest.java:85-91`, `ExamOrderMessagingIntegrationTest.java:163-172`).
- [x] `GET /notifications?patientId={uuid}` para paciente sem notificações retorna `200 OK` com lista vazia na página (`NotificationControllerTest.java:71-86`).

---

## Gate Check

- **Gate command**: `cd /Users/Vini/Projetos/Programacao/java/medflow/medflow && DOCKER_HOST=unix:///Users/Vini/.orbstack/run/docker.sock ./mvnw test`
- **Result**: 130 passed, 0 failed, 0 errors, 0 skipped
- **Test count before feature**: 97 (commit `2c705e7`)
- **Test count after feature**: 130 (commit `28e8e13`)
- **Delta**: +33 new tests (+22 no Round 1, +11 no commit `28e8e13`)
- **Skipped tests**: nenhum
- **Failures**: nenhum

---

## Requirement Traceability Update

`spec.md` atualizado para `Verified` nos 4 requisitos:

| Requirement | Previous Status | New Status |
| ----------- | --------------- | ---------- |
| FLOW-01 | Implementing | ✅ Verified |
| FLOW-02 | Implementing | ✅ Verified |
| FLOW-03 | Implementing | ✅ Verified |
| FLOW-04 | Implementing | ✅ Verified |

---

## Summary

**Overall**: ✅ Ready

**Spec-anchored check**: 15/15 ACs matched spec outcome | 0 spec-precision gaps
**Sensor**: 8/8 mutations killed (0 survived)
**Gate**: 130 passed, 0 failed, 0 errors, 0 skipped

**What works**:
- Ciclo completo Hospital → Outbox → Kafka (`exam-orders.v1`) → Laboratório (`AWAITING_COLLECTION`) → Evolução (`PATCH /exam-orders/{id}/status`) → Entrega de Laudo (`POST /exam-orders/{id}/result` + `ExamResult` + `RELEASED`) → Outbox → Kafka (`exam-notifications.v1`) → Notificações (`notifications` + `GET /notifications` ordenado por `createdAt DESC`) verificado com Testcontainers reais (PostgreSQL 17 + Flyway V4 + Apache Kafka 3.8.0).
- Todas as 5 lacunas do Round 1 e ambos os mutantes sobreviventes foram fechados em `28e8e13` e validados empiricamente.

**Issues found**: none

**Next steps**: Feature verificada e pronta para revisão/integração; lições candidatas pré-existentes (`L-001`..`L-003`) permanecem registradas sem duplicação em `.specs/lessons.json`.
