# Design — Fluxo Completo: Hospital, Laboratório Unificado, Evolução, Resultados e Notificações

## 1. Atores e Responsabilidades (4 Atores Reais)

1. **Recepção do Hospital:** Cadastra e consulta a ficha do paciente (`PatientEntity` — `POST /patients`, `GET /patients/{id}`).
2. **Médico no Consultório:** Consulta a ficha do paciente e solicita exames dentre os cadastrados no catálogo do laboratório (`ExamOrder` — `POST /exam-orders`), gerando pedido em `RECEIVED` e gravando `ExamOrderCreatedEvent` na tabela `outbox_events`.
3. **Laboratório (Entidade Unificada):**
   - Disponibiliza o catálogo de exames (`ExamDefinition` — `POST /exam-definitions`, `PATCH /exam-definitions/{id}/deactivation`).
   - Aceita solicitações dos médicos via Kafka (`LaboratoryEventListener`, grupo `laboratory-group` ➔ `AWAITING_COLLECTION`).
   - Faz a evolução operacional do exame (`PATCH /exam-orders/{id}/status` ➔ `COLLECTED`, `IN_TRANSIT`, `RECEIVED_AT_LAB`, `PROCESSING`, `VALIDATED`, ou `REJECTED`/`CANCELLED`) gravando `ExamLaboratoryUpdatedEvent` na `outbox_events`.
   - Entrega o resultado final ao paciente (`POST /exam-orders/{id}/result` ➔ salva `ExamResult` na tabela `exam_results`, muda `ExamOrder` para `RELEASED` e grava `ExamLaboratoryUpdatedEvent` na `outbox_events`).
4. **Paciente (e Recepção na saída/acompanhamento):** Lê notificações em `GET /notifications?patientId={id}` e consulta seu laudo em `GET /exam-orders/{id}/result`.

---

## 2. Mapa de Comunicação: Síncrono vs. Assíncrono

### Viagem Assíncrona 1: Médico (Hospital) ➔ Laboratório
- **Produtor:** `ExamOrderService.create()` salva `ExamOrder(RECEIVED)` e grava `ExamOrderCreatedEvent` na `outbox_events`.
- **Publicador:** `OutboxPublisher` (`@Scheduled 2s`) publica no tópico **`exam-orders.v1`** (key = `orderId`).
- **Consumidor:** `LaboratoryEventListener` (`groupId = "laboratory-group"`), que valida idempotência em `processed_events(event_id, 'laboratory-group')` e muda a `ExamOrder` de `RECEIVED` ➔ **`AWAITING_COLLECTION`**.

### Viagem Assíncrona 2: Laboratório ➔ Paciente (Notificações)
- **Produtor:** `ExamOrderService.updateStatus()` (`COLLECTED`, `PROCESSING`, `VALIDATED`, `REJECTED`, `CANCELLED`, etc.) e `ExamOrderService.registerResult()` (`RELEASED` + `ExamResult`) gravam `ExamLaboratoryUpdatedEvent` na `outbox_events`.
- **Publicador:** `OutboxPublisher` detecta `eventType = "ExamLaboratoryUpdatedEvent"` e publica no tópico **`exam-notifications.v1`** (key = `orderId`).
- **Consumidor:** `NotificationEventListener` (`groupId = "notification-group"`), que valida idempotência em `processed_events(event_id, 'notification-group')` e salva a entidade **`Notification`** na tabela `notifications`.

---

## 3. Regras de Transição de Estado (`ExamOrder`)

- `acceptByLaboratory()`: só altera `RECEIVED` ➔ `AWAITING_COLLECTION`.
- `updateStatus(ExamStatus newStatus)`:
  - Bloqueia se `newStatus == null`, `newStatus == ExamStatus.RECEIVED` ou `newStatus == ExamStatus.RELEASED` (pois `RELEASED` exige um `ExamResult` via `POST /result`).
  - Bloqueia se a `ExamOrder` já estiver em estado terminal (`RELEASED`, `REJECTED`, `CANCELLED`).
- `markReleased()`: bloqueia se já estiver `RELEASED`, `REJECTED` ou `CANCELLED`; caso contrário muda para `RELEASED`.

---

## 4. Schema (`V4__create_exam_results_and_notifications_tables.sql`)

- **`exam_results`**: `id` (`UUID` PK), `order_id` (`UUID` NOT NULL UNIQUE FK `exam_orders.id`), `patient_id` (`UUID` NOT NULL FK `patients.id`), `exam_code` (`VARCHAR(50)`), `result_text` (`TEXT` NOT NULL), `released_at` (`TIMESTAMPTZ`).
- **`notifications`**: `id` (`UUID` PK), `event_id` (`UUID`), `order_id` (`UUID` NOT NULL FK `exam_orders.id`), `patient_id` (`UUID` NOT NULL FK `patients.id`), `exam_code` (`VARCHAR(50)`), `status` (`VARCHAR(30)`), `message` (`VARCHAR(500)`), `created_at` (`TIMESTAMPTZ`).

---

## 5. Tratamento de Erros (RFC 9457 / `GlobalExceptionHandler`)

- `ExamOrderStatusTransitionException` -> `409 Conflict` (`urn:medflow:problem:exam-order-status-transition-invalid`).
- `ExamResultConflictException` -> `409 Conflict` (`urn:medflow:problem:exam-result-conflict`).
- `ExamResultNotFoundException` -> `404 Not Found` (`urn:medflow:problem:exam-result-not-found`).
