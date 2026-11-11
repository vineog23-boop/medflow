# Fluxo Completo: Hospital, Laboratório Unificado, Evolução de Exames, Resultados e Notificações

## Problem Statement

O Medflow precisa refletir com fidelidade os papéis reais entre o **Hospital** (Recepção e Consultório Médico), o **Laboratório** e o **Paciente**. Nesse domínio, um **único Laboratório** desempenha todo o ciclo laboratorial: (1) disponibiliza o catálogo de exames possíveis (`ExamDefinition`); (2) recebe e aceita via Kafka as solicitações enviadas pelo médico (`RECEIVED` ➔ `AWAITING_COLLECTION`); (3) registra a evolução operacional do exame (`COLLECTED`, `IN_TRANSIT`, `RECEIVED_AT_LAB`, `PROCESSING`, `VALIDATED`, ou `REJECTED`/`CANCELLED` em caso de impedimento); e (4) realiza a entrega final do resultado na entidade separada `ExamResult` (`RELEASED`), disparando eventos no Kafka para que o módulo de Notificações avise o Paciente (consultável em `GET /notifications` e `GET /exam-orders/{id}/result`).

## Goals

- [ ] **Laboratório Unificado no Catálogo e Recebimento (Médico ➔ Kafka ➔ Laboratório):** Manter `ExamDefinition` sob responsabilidade do Laboratório e implementar `LaboratoryEventListener` (`laboratory-group`) para consumir `ExamOrderCreatedEvent` de `exam-orders.v1`, aceitando automaticamente o pedido (`RECEIVED` ➔ `AWAITING_COLLECTION`).
- [ ] **Evolução Operacional do Exame pelo Laboratório:** Expor `PATCH /exam-orders/{id}/status` para o Laboratório evoluir o status da `ExamOrder` (`COLLECTED`, `IN_TRANSIT`, `RECEIVED_AT_LAB`, `PROCESSING`, `VALIDATED`, `REJECTED`, `CANCELLED`), bloqueando transição direta para `RELEASED` sem laudo `ExamResult` e registrando `ExamLaboratoryUpdatedEvent` na Outbox.
- [ ] **Entrega do Exame para o Paciente (`ExamResult`):** Expor `POST /exam-orders/{id}/result` (que persiste `ExamResult` em `exam_results`, muda a `ExamOrder` para `RELEASED` e grava `ExamLaboratoryUpdatedEvent` na Outbox) e `GET /exam-orders/{id}/result` para consulta do resultado.
- [ ] **Notificações Assíncronas para o Paciente (Laboratório ➔ Kafka ➔ Paciente):** Atualizar `NotificationEventListener` (`notification-group`) para consumir `exam-notifications.v1`, persistir avisos reais na tabela `notifications` a cada evolução relevante/entrega e expor `GET /notifications` (paginado e com filtro opcional por `patientId`).

## Out of Scope

| Feature | Reason |
| --- | --- |
| Cadastro e tabela própria de Médicos (`Doctor`) | O médico atua como solicitante na rota `POST /exam-orders` após consultar a ficha do paciente. |
| Tabela separada de amostras físicas (`Sample`) | A evolução do exame no laboratório é rastreada pelos estados de `ExamStatus` na `ExamOrder`, e os dados clínicos do cliente ficam em `ExamResult`. |
| Envio externo real por SMTP, SMS ou WhatsApp | As notificações geradas via Kafka são persistidas no PostgreSQL e consultadas em `GET /notifications`. |
| Autenticação JWT e controle de acesso por papel | Segurança e RBAC serão adicionados em etapa posterior. |

---

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- | --- |
| Papel unificado do Laboratório | O mesmo Laboratório cria `ExamDefinition`, aceita pedidos via Kafka, evolui o `ExamStatus` e entrega `ExamResult` | Alinha toda a responsabilidade laboratorial sem dividir artificialmente "coordenação" e "execução". | y |
| Aceite automático do pedido pelo Laboratório | `LaboratoryEventListener` (`laboratory-group`) muda `RECEIVED` ➔ `AWAITING_COLLECTION`; impedimentos usam `PATCH /status` (`REJECTED`/`CANCELLED`) | Evita um passo manual extra para aceitar cada solicitação válida e ainda permite rejeição/cancelamento pelo Lab. | y |
| Evolução de status e proteção de `RELEASED` | `PATCH /exam-orders/{id}/status` evolui etapas intermediárias e rejeita `RELEASED` direto (`409 Conflict`); apenas `POST /exam-orders/{id}/result` libera como `RELEASED` | Garante invariante de negócio: nenhum exame fica `RELEASED` sem ter um laudo `ExamResult` persistido para o paciente. | y |
| Separação entre pedido e resultado | Tabela `exam_results` (entidade `ExamResult` com `id`, `orderId`, `patientId`, `examCode`, `resultText`, `releasedAt`) | Separa o rastreamento operacional (`ExamOrder`) dos dados clínicos pertencentes ao paciente (`ExamResult`). | y |

**Open questions: none**

---

## User Stories

### P1: Aceitar solicitação médica no Laboratório via Kafka ⭐ MVP

**User Story**: Como Laboratório (que já disponibilizou o catálogo em `ExamDefinition`), quero receber via Kafka os pedidos solicitados pelos médicos para aceitá-los automaticamente em fila de coleta (`AWAITING_COLLECTION`).

**Why P1**: Conecta assincronamente o consultório médico ao laboratório através do tópico `exam-orders.v1`.

**Acceptance Criteria**:

1. WHEN o médico solicita um exame ativo em `POST /exam-orders`, a aplicação SHALL salvar a `ExamOrder` com status `RECEIVED` e gravar um `OutboxEvent` (`PENDING`) para o tópico `exam-orders.v1`.
2. WHEN o `LaboratoryEventListener` (`laboratory-group`) consome um `ExamOrderCreatedEvent` inédito de `exam-orders.v1` e o pedido está em `RECEIVED`, a aplicação SHALL atualizar o status da `ExamOrder` para `AWAITING_COLLECTION` e registrar o `ProcessedEvent` para `laboratory-group`.
3. IF o `LaboratoryEventListener` receber um `ExamOrderCreatedEvent` já registrado em `processed_events` para `laboratory-group`, THEN a aplicação SHALL descartar a mensagem sem alterar a `ExamOrder`.
4. WHILE uma `ExamOrder` já estiver em um status diferente de `RECEIVED`, WHEN um `ExamOrderCreatedEvent` atrasado for consumido pelo `LaboratoryEventListener`, a aplicação SHALL manter o status atual sem retroceder o estado.

**Independent Test**: Criar pedido via `ExamOrderService.create()`, rodar `OutboxPublisher` e verificar que o `LaboratoryEventListener` muda o status do pedido para `AWAITING_COLLECTION`.

---

### P1: Evoluir etapas do exame pelo Laboratório (`PATCH /status`) ⭐ MVP

**User Story**: Como Laboratório, quero registrar cada evolução do exame (`COLLECTED`, `IN_TRANSIT`, `RECEIVED_AT_LAB`, `PROCESSING`, `VALIDATED`, ou `REJECTED`/`CANCELLED`) para manter o histórico atualizado e notificar o paciente via Kafka.

**Why P1**: Dá vida útil a todos os estados do enum `ExamStatus` e dispara notificações reais a cada avanço no laboratório.

**Acceptance Criteria**:

1. WHEN o Laboratório envia `PATCH /exam-orders/{id}/status` com um `ExamStatus` operacional válido (exceto `RECEIVED` e `RELEASED`) para um pedido não finalizado, a API SHALL atualizar o status da `ExamOrder`, gravar um `OutboxEvent` (`ExamLaboratoryUpdatedEvent`) na mesma transação e responder `200 OK` com `ExamOrderResponseDto`.
2. IF o Laboratório enviar `PATCH /exam-orders/{id}/status` tentando definir `status = RELEASED` diretamente sem cadastrar o laudo ou tentar alterar um pedido já finalizado (`RELEASED`, `REJECTED`, `CANCELLED`), THEN a API SHALL rejeitar a operação com `409 Conflict` no padrão RFC 9457 (`urn:medflow:problem:exam-order-status-transition-invalid`).
3. IF o pedido informado em `PATCH /exam-orders/{id}/status` não existir, THEN a API SHALL responder `404 Not Found` (`urn:medflow:problem:exam-order-not-found`).
4. IF o corpo de `PATCH /exam-orders/{id}/status` contiver `status` nulo, THEN a API SHALL responder `422 Unprocessable Content` com JSON Pointer `#/status`.

**Independent Test**: Chamar `PATCH /exam-orders/{id}/status` com `COLLECTED` e `PROCESSING`, conferindo status `200`, evento gravado na `outbox_events` e rejeição `409` ao tentar `RELEASED` sem laudo.

---

### P1: Entregar o resultado do exame (`ExamResult`) para o Paciente ⭐ MVP

**User Story**: Como Laboratório, quero registrar e liberar o laudo clínico (`ExamResult`) de um pedido para que o paciente possa consultar o seu resultado final e ser notificado da entrega.

**Why P1**: Materializa a entrega do exame ao paciente em tabela separada (`exam_results`) com os dados clínicos úteis apenas ao cliente.

**Acceptance Criteria**:

1. WHEN o Laboratório envia `POST /exam-orders/{id}/result` com `resultText` válido para um pedido existente sem resultado prévio (não `CANCELLED`/`REJECTED`), a API SHALL persistir a entidade `ExamResult` na tabela `exam_results`, atualizar a `ExamOrder` para `RELEASED`, gravar `ExamLaboratoryUpdatedEvent` na `outbox_events` e responder `201 Created` com `ExamResultResponseDto`.
2. IF o Laboratório tentar cadastrar um segundo resultado (`POST /exam-orders/{id}/result`) para um pedido que já possui `ExamResult` ou que está `CANCELLED`/`REJECTED`, THEN a API SHALL responder `409 Conflict` no padrão RFC 9457 (`urn:medflow:problem:exam-result-conflict`).
3. WHEN o Paciente (ou Médico) chama `GET /exam-orders/{id}/result` para um pedido com resultado liberado, a API SHALL responder `200 OK` com o `ExamResultResponseDto`.
4. IF `GET /exam-orders/{id}/result` for chamado para um pedido cujo resultado ainda não foi liberado, THEN a API SHALL responder `404 Not Found` (`urn:medflow:problem:exam-result-not-found`).

**Independent Test**: Chamar `POST /exam-orders/{id}/result` e em seguida `GET /exam-orders/{id}/result`, verificando a tabela `exam_results`, o status `RELEASED` na `exam_orders` e o evento na Outbox.

---

### P1: Notificar o Paciente via Kafka e expor consulta de avisos ⭐ MVP

**User Story**: Como Paciente (ou Recepção), quero receber notificações assíncronas a cada evolução ou entrega de exame pelo laboratório e consultá-las via `GET /notifications`.

**Why P1**: Fecha o ciclo assíncrono do módulo `notification`, permitindo que o paciente acompanhe a evolução e a entrega do exame.

**Acceptance Criteria**:

1. WHEN o `NotificationEventListener` (`notification-group`) consome um `ExamLaboratoryUpdatedEvent` inédito do tópico `exam-notifications.v1`, a aplicação SHALL salvar uma entidade `Notification` na tabela `notifications` (com `orderId`, `patientId`, `examCode`, `status`, `message` e `createdAt`) e gravar o `ProcessedEvent`.
2. IF o `NotificationEventListener` receber um `ExamLaboratoryUpdatedEvent` cujo `eventId` já exista em `processed_events` para `notification-group`, THEN a aplicação SHALL ignorar a duplicata sem inserir nova linha em `notifications`.
3. WHEN o Paciente ou a Recepção chama `GET /notifications` (com filtro opcional `?patientId={uuid}`), a API SHALL responder `200 OK` com `Page<NotificationResponseDto>` ordenado do aviso mais recente para o mais antigo.

**Independent Test**: Evoluir um pedido (`COLLECTED`) e liberar seu resultado (`RELEASED`), publicar via `OutboxPublisher` no Kafka e verificar os 2 avisos retornados em `GET /notifications?patientId={id}`.

---

## Edge Cases

- IF uma mensagem idêntica chegar duas vezes para `laboratory-group` (`exam-orders.v1`) ou `notification-group` (`exam-notifications.v1`), THEN o respectivo listener SHALL descartar a duplicata consultando `processed_events(event_id, consumer_group)`.
- IF `GET /notifications?patientId={uuid}` for chamado para um paciente sem notificações, THEN a API SHALL responder `200 OK` com lista vazia na página.

---

## Requirement Traceability

| Requirement ID | Story | Phase | Status |
| --- | --- | --- | --- |
| FLOW-01 | P1: Aceitar solicitação médica no Laboratório via Kafka | Execute | Implementing |
| FLOW-02 | P1: Evoluir etapas do exame pelo Laboratório (PATCH /status) | Execute | Implementing |
| FLOW-03 | P1: Entregar o resultado do exame (ExamResult) para o Paciente | Execute | Implementing |
| FLOW-04 | P1: Notificar o Paciente via Kafka e expor consulta de avisos | Execute | Implementing |

---

## Success Criteria

- [ ] Migration `V4__create_exam_results_and_notifications_tables.sql` aplicada pelo Flyway e validada pelo Hibernate (`ddl-auto=validate`).
- [ ] Laboratório unificado gerenciando catálogo (`ExamDefinition`), aceite via Kafka (`AWAITING_COLLECTION`), evolução (`PATCH /status`) e entrega de laudo (`POST /result` -> `ExamResult` + `RELEASED`).
- [ ] Paciente e Recepção consultando notificações geradas pelo Kafka em `GET /notifications` e laudo clínico em `GET /exam-orders/{id}/result`.
- [ ] Suíte completa de testes unitários, `MockMvc` e integração E2E com `Testcontainers` verde.
