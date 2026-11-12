<h1 align="center">🩺 Medflow</h1>
<p align="center">API clínica para gestão de pedidos laboratoriais com persistência transacional, mensageria via Apache Kafka (Transactional Outbox) e testes automatizados de integração.</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-2563EB?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java: 21">
  <img src="https://img.shields.io/badge/Spring_Boot-4.1.1-0F766E?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot: 4.1.1">
  <img src="https://img.shields.io/badge/PostgreSQL-16-336791?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL: 16">
  <img src="https://img.shields.io/badge/Apache_Kafka-3.8.0-231F20?style=for-the-badge&logo=apachekafka&logoColor=white" alt="Apache Kafka: 3.8.0">
  <img src="https://img.shields.io/badge/Flyway-DB-CC0200?style=for-the-badge&logo=flyway&logoColor=white" alt="Flyway Migration">
  <img src="https://img.shields.io/badge/Docker_Compose-Infrastructure-2496ED?style=for-the-badge&logo=docker&logoColor=white" alt="Docker Compose">
  <img src="https://img.shields.io/badge/Status-Em_desenvolvimento-F59E0B?style=for-the-badge" alt="Status: Em desenvolvimento">
</p>

<p align="center">
  <a href="#visão-geral">Visão Geral</a> · 
  <a href="#funcionalidades">Funcionalidades</a> · 
  <a href="#tecnologias">Tecnologias</a> · 
  <a href="#arquitetura">Arquitetura</a> · 
  <a href="#mensageria-e-consistência">Mensageria & Outbox</a> · 
  <a href="#como-rodar">Como Rodar</a> · 
  <a href="#endpoints">Endpoints</a> · 
  <a href="#testes">Testes</a> · 
  <a href="#desafios-enfrentados">Desafios</a>
</p>

---

## Visão Geral

O **Medflow** é uma API REST desenvolvida em Java 21 e Spring Boot para apoiar o fluxo clínico e laboratorial ponta a ponta: recepção e cadastro de pacientes, catálogo de exames, criação de ordens de serviço, triagem laboratorial, evolução de status, emissão de laudo clínico e histórico de notificações ao paciente.

O projeto é estruturado como um **monólito modular** baseado em fatias verticais (*vertical slices*), aplicando comunicação assíncrona orientada a eventos com Apache Kafka e o padrão de resiliência **Transactional Outbox**. Ao criar uma ordem ou alterar seu status clínico, as mutações de banco e a intenção de publicação são gravadas atomicamente na mesma transação PostgreSQL, eliminando o risco de inconsistências (*dual-write*).

---

## Funcionalidades

### 👤 Gestão de Pacientes
- ✅ Cadastro de pacientes com validação estrita de CPF, nome completo e data de nascimento no passado;
- ✅ Consulta paginada, busca individual por UUID, atualização cadastral e remoção com integridade referencial;

### 🧪 Catálogo de Definições de Exame
- ✅ Cadastro de tipos de exame no catálogo (código mnemônico único, nome descritivo e tipo de amostra: `BLOOD`, `URINE`, etc.);
- ✅ Listagem geral, busca por ID, atualização de metadados e desativação lógica (preservando o histórico clínico);

### 📋 Ordens de Exame (Pedidos Médicos)
- ✅ Abertura de ordens vinculadas a um paciente existente e a uma definição de exame ativa no catálogo;
- ✅ Definição de nível de prioridade (`NORMAL` ou `URGENT`) e inicialização obrigatória no status `RECEIVED`;
- ✅ Concorrência otimista com `@Version` em nível de entidade para proteção contra perda de atualizações;
- ✅ Bloqueio de alteração e exclusão para ordens que já avançaram além do status inicial `RECEIVED`;

### 🔬 Fluxo Operacional Laboratorial
- ✅ Consumidor assíncrono Kafka (`laboratory-group`) no tópico `exam-orders.v1`: ao receber um novo pedido, evolui o status automaticamente para `AWAITING_COLLECTION`;
- ✅ Endpoint operacional (`PATCH /exam-orders/{id}/status`) para evolução de status da triagem (`COLLECTED`, `IN_TRANSIT`, `PROCESSING`, `VALIDATED`, etc.);
- ✅ Máquina de estados com validação de transições válidas e bloqueio de alteração para ordens em estados terminais (`RELEASED`, `REJECTED`, `CANCELLED`);

### 📄 Laudo Clínico (Resultados)
- ✅ Registro de laudo clínico imutável via `POST /exam-orders/{id}/result`, vinculando o resultado textual à ordem;
- ✅ Transição automática da ordem para o status terminal `RELEASED`;
- ✅ Bloqueio contra laudos duplicados para a mesma ordem (retornando `409 Conflict`);
- ✅ Consulta do laudo clínico e metadados de liberação via `GET /exam-orders/{id}/result`;

### 🔔 Notificações ao Paciente
- ✅ Consumidor assíncrono Kafka (`notification-group`) no tópico `exam-notifications.v1` escutando eventos de evolução laboratorial e laudos emitidos;
- ✅ Deduplicação idempotente garantindo processamento único mesmo sob reentregas do broker;
- ✅ Persistência das notificações no banco de dados (`notifications`) com canal `IN_APP` e mensagens contextuais amigáveis;
- ✅ Endpoint `GET /notifications?patientId={id}` para consulta paginada ordenada por data decrescente;

### 🛡️ Arquitetura, Resiliência e Infraestrutura
- ✅ Padrão **Transactional Outbox**: inserção atômica em `outbox_events` na mesma transação de negócio;
- ✅ Publicador agendado (`OutboxPublisher`) a cada 2s com chave de partição por `orderId` e controle de status (`PENDING`, `PUBLISHED`, `FAILED`);
- ✅ Deduplicação em nível de consumidor com tabela `processed_events` e chave composta `(event_id, consumer_group)`;
- ✅ Resiliência com `DefaultErrorHandler`, backoff exponencial e direcionamento para Dead Letter Topic (`.DLT`);
- ✅ Schema relacional versionado com 4 migrações Flyway e validação de mapeamento via `ddl-auto=validate`;
- ✅ Contratos de erro HTTP padronizados conforme a **RFC 9457** (`ProblemDetail`);
- ✅ Suíte de **140 testes automatizados**, incluindo integração completa com containers reais (Testcontainers);
- 🚧 Autenticação e autorização stateless com Spring Security;
- 🚧 Envio de notificações externas reais (SMS / E-mail);
- 🚧 Documentação interativa via Swagger / OpenAPI 3.

---

## Tecnologias

| Tecnologia | Versão / Uso | Descrição |
| --- | --- | --- |
| **Java** | 21 | Linguagem de programação com uso de records, pattern matching e virtual threads. |
| **Spring Boot** | 4.1.1 | Framework base para configuração automática e ecossistema de microsserviços. |
| **Spring Web MVC** | Starter | Camada web HTTP RESTful e controllers. |
| **Spring Data JPA** | Starter | Camada de persistência relacional e repositórios. |
| **Hibernate** | Via Spring Data | ORM e validação de schema em tempo de inicialização (`ddl-auto=validate`). |
| **PostgreSQL** | 16 (Alpine) | Banco de dados relacional principal da aplicação e dos testes. |
| **Flyway** | 10.x | Gerenciamento e versionamento incremental de migrações SQL (`V1` a `V4`). |
| **Apache Kafka** | 3.8.0 (KRaft) | Broker de mensageria assíncrona distribuída em modo KRaft (sem ZooKeeper). |
| **Spring Kafka** | Starter | `KafkaTemplate`, listeners `@KafkaListener`, retentativas e recuperação com DLT. |
| **Bean Validation** | Starter | Validação declarativa de entrada via anotações nos DTOs de borda. |
| **Jackson JSR310** | 2.15.2 | Serialização e desserialização de datas Java 8+ (`Instant`, `LocalDate`) em ISO-8601. |
| **Testcontainers** | 1.19+ | Execução de instâncias reais e isoladas de PostgreSQL e Kafka para a suíte de testes. |
| **JUnit 5 & Mockito** | Starter Test | Testes unitários puros, testes de service com mocks e validações de contratos. |
| **Awaitility** | 4.2+ | Asserções assíncronas determinísticas para testes de mensageria com eventos e concorrência. |
| **Docker Compose** | 2.x | Orquestração da infraestrutura local de PostgreSQL e Kafka broker. |

---

## Arquitetura

O projeto adota uma arquitetura em **fatias verticais** (*vertical slices*) dentro de um monólito modular. Cada módulo de negócio agrupa suas próprias regras de domínio, casos de uso de aplicação, persistência JPA e controladores HTTP, reduzindo o acoplamento entre domínios distintos:

```text
medflow/
├── src/main/java/br/com/medflow/
│   ├── MedflowApplication.java                    # Ponto de entrada da aplicação Spring Boot
│   ├── patient/                                   # Contexto de Pacientes
│   │   ├── application/                           # Casos de uso, DTOs e regras de serviço de pacientes
│   │   ├── domain/                                # Entidade Patient e validação estrita de invariantes
│   │   ├── persistence/                           # Repositório Spring Data JPA de pacientes
│   │   └── web/                                   # Controller HTTP REST (/patients)
│   ├── exam/
│   │   ├── definition/                            # Contexto do Catálogo de Exames
│   │   │   ├── application/                       # Casos de uso e DTOs de definições
│   │   │   ├── domain/                            # Entidade ExamDefinition e regras de ativação
│   │   │   ├── persistence/                       # Repositório JPA de definições
│   │   │   └── web/                               # Controller HTTP REST (/exam-definitions)
│   │   └── order/                                 # Contexto de Ordens de Exame e Laudos
│   │       ├── application/                       # Casos de uso de pedidos, transições e laudos clínicos
│   │       │   ├── dto/request/                   # DTOs de entrada validados com Bean Validation
│   │       │   ├── dto/response/                  # DTOs de saída imutáveis (records)
│   │       │   └── exception/                     # Exceções de negócio (transição inválida, laudo duplicado)
│   │       ├── domain/                            # Entidades ExamOrder, ExamResult e máquina de status
│   │       ├── persistence/                       # Repositórios JPA de ordens e resultados clínicos
│   │       └── web/                               # Controller HTTP REST (/exam-orders)
│   ├── laboratory/
│   │   └── application/                           # Consumidor Kafka (LaboratoryEventListener - laboratory-group)
│   ├── notification/
│   │   ├── application/                           # Consumidor Kafka (NotificationEventListener - notification-group)
│   │   │   ├── dto/response/                      # DTOs de retorno para a consulta de notificações
│   │   │   └── NotificationService.java           # Consulta paginada de notificações do paciente
│   │   ├── domain/                                # Entidade Notification e validação de canal
│   │   ├── persistence/                           # Repositório JPA de notificações
│   │   └── web/                                   # Controller HTTP REST (/notifications)
│   ├── messaging/
│   │   ├── config/                                # Configuração do KafkaTemplate, Consumers, Retries e DLT
│   │   ├── outbox/                                # Padrão Transactional Outbox
│   │   │   ├── domain/                            # Entidade OutboxEvent e status (PENDING, PUBLISHED, FAILED)
│   │   │   ├── persistence/                       # Repositório JPA com lock e paginação de eventos
│   │   │   └── publisher/                         # OutboxPublisher agendado com roteamento de tópicos
│   │   └── consumer/                              # Deduplicação idempotente de consumo
│   │       ├── domain/                            # Entidade ProcessedEvent e chave composta ProcessedEventId
│   │       └── persistence/                       # Repositório JPA para registro de eventos processados
│   └── shared/                                    # Infraestrutura compartilhada
│       ├── config/                                # Configuração do Jackson ObjectMapper e datas ISO-8601
│       └── web/error/                             # GlobalExceptionHandler padronizado na RFC 9457 (ProblemDetail)
├── src/main/resources/
│   ├── application.properties                     # Configurações de ambiente, JPA, Flyway e Kafka
│   └── db/migration/                              # Migrações versionadas Flyway (V1 a V4)
└── src/test/java/br/com/medflow/                  # 140 testes automatizados (unitários, web, persistência e E2E)
```

---

## Mensageria e Consistência

Para garantir consistência eventual sem recorrer a transações distribuídas lentas (2PC / XA), o Medflow adota o padrão **Transactional Outbox**:

```text
                             FLUXO ASSÍNCRONO MEDFLOW
                             
1. POST /exam-orders
   │
   ▼
┌────────────────────────────────────────────────────────┐
│ Transação PostgreSQL (Commit Atômico)                  │
│  ├─ INSERT INTO exam_orders (status = 'RECEIVED')      │
│  └─ INSERT INTO outbox_events (ExamOrderCreatedEvent)  │
└───────────────────────────────────┬────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────┐
│ OutboxPublisher (Agendado a cada 2s)                   │
│  ├─ SELECT * FROM outbox_events WHERE status='PENDING' │
│  ├─ Envia mensagem com chave = orderId                 │
│  └─ UPDATE outbox_events SET status = 'PUBLISHED'      │
└───────────────────┬────────────────────────────────────┘
                    │ Tópico: exam-orders.v1
                    ▼
┌────────────────────────────────────────────────────────┐
│ LaboratoryEventListener (laboratory-group)             │
│  ├─ Verifica se (eventId, 'laboratory-group') já existe│
│  ├─ Registra em processed_events                       │
│  └─ Transiciona ordem para 'AWAITING_COLLECTION'       │
└────────────────────────────────────────────────────────┘

2. PATCH /exam-orders/{id}/status  OU  POST /exam-orders/{id}/result
   │
   ▼
┌────────────────────────────────────────────────────────┐
│ Transação PostgreSQL (Commit Atômico)                  │
│  ├─ UPDATE exam_orders (status = COLLECTED / RELEASED) │
│  ├─ INSERT INTO exam_results (se laudo clínico)        │
│  └─ INSERT INTO outbox_events (ExamLaboratoryUpdated)  │
└───────────────────┬────────────────────────────────────┘
                    │ Tópico: exam-notifications.v1
                    ▼
┌────────────────────────────────────────────────────────┐
│ NotificationEventListener (notification-group)         │
│  ├─ Verifica se (eventId, 'notification-group') existe │
│  ├─ Registra em processed_events                       │
│  └─ INSERT INTO notifications (aviso gerado)           │
└────────────────────────────────────────────────────────┘
```

### Características de Resiliência:
- **Zero Dual-Write:** O evento de domínio só existe se a transação do banco relacional for confirmada com sucesso.
- **Roteamento de Tópicos:**
  - `ExamOrderCreatedEvent` ➡️ `exam-orders.v1` (chave de partição: `orderId`).
  - `ExamLaboratoryUpdatedEvent` ➡️ `exam-notifications.v1` (chave de partição: `orderId`).
- **Deduplicação Idempotente:** A tabela `processed_events` utiliza chave primária composta `(event_id, consumer_group)`. Se uma reentrega de rede ocorrer, o consumidor detecta o registro prévio e descarta o processamento sem efeitos colaterais.
- **Dead Letter Topic (DLT):** Configurado via `DefaultErrorHandler` com backoff exponencial. Se o processamento de um evento falhar repetidamente, a mensagem é encaminhada para o tópico correspondente com sufixo `.DLT`.

---

## Modelagem de Dados & Flyway Migrations

O schema do banco relacional é gerenciado de forma estrita e imutável pelo Flyway. O Hibernate atua exclusivamente no modo `validate`, garantindo que nenhuma alteração ocorra sem uma migração SQL versionada correspondente:

| Migração | Descrição | Tabelas Afetadas |
| --- | --- | --- |
| `V1__create_initial_schema.sql` | Criação das tabelas centrais de pacientes, definições e ordens | `patients`, `exam_definitions`, `exam_orders` |
| `V2__create_outbox_events_table.sql` | Criação da tabela da Transactional Outbox com índices por status e data | `outbox_events` |
| `V3__create_processed_events_table.sql` | Criação da tabela de controle de deduplicação e idempotência de eventos | `processed_events` |
| `V4__create_exam_results_and_notifications_tables.sql` | Criação das tabelas de laudos clínicos imutáveis e histórico de notificações | `exam_results`, `notifications` |

---

## Como Rodar

### Pré-requisitos
- **JDK 21** instalado e configurado no `JAVA_HOME`.
- **Docker** e **Docker Compose** (via Docker Desktop, OrbStack ou Podman).

### 1. Com Docker Compose para a infraestrutura (Recomendado)

Na raiz do repositório, inicie os containers do PostgreSQL e do Kafka:

```bash
docker compose up -d
```

O compose subirá:
- **PostgreSQL 16** na porta `localhost:5432` (database: `medflow_db`, user: `postgres`, password: `password`).
- **Apache Kafka 3.8.0** na porta `localhost:9092` (em modo KRaft, sem dependência do ZooKeeper).

Em seguida, entre na pasta da aplicação e execute via Maven Wrapper:

```bash
cd medflow
./mvnw spring-boot:run
```

A aplicação inicializará na porta padrão `8080`, executando automaticamente as 4 migrações do Flyway.

Para encerrar a infraestrutura preservando os dados gravados nos volumes:
```bash
docker compose stop
```

---

### 2. Sem Docker Compose (Banco e Broker Externos)

Se você já possui instâncias locais ou remotas do PostgreSQL e do Apache Kafka, defina as variáveis de ambiente antes de inicializar:

```bash
export DB_URL="jdbc:postgresql://seu-host:5432/medflow_db"
export DB_USERNAME="seu_usuario"
export DB_PASSWORD="sua_senha"
export KAFKA_BOOTSTRAP_SERVERS="seu-broker:9092"

cd medflow
./mvnw spring-boot:run
```

*(No Windows PowerShell, utilize `$env:DB_URL="jdbc:postgresql://..."`)*.

---

## Endpoints

Abaixo estão listadas todas as rotas implementadas na API:

| Módulo | Método | Endpoint | Descrição |
| --- | --- | --- | --- |
| **Pacientes** | `POST` | `/patients` | Cadastra um novo paciente. |
| | `GET` | `/patients` | Lista pacientes de forma paginada (`page`, `size`, `sort`). |
| | `GET` | `/patients/{id}` | Busca um paciente por UUID. |
| | `PUT` | `/patients/{id}` | Atualiza nome completo ou data de nascimento de um paciente. |
| | `DELETE` | `/patients/{id}` | Remove um paciente da base. |
| **Catálogo** | `POST` | `/exam-definitions` | Cadastra um novo tipo de exame no catálogo. |
| | `GET` | `/exam-definitions` | Lista todas as definições ativas e inativas. |
| | `GET` | `/exam-definitions/{id}` | Busca uma definição de exame por UUID. |
| | `PUT` | `/exam-definitions/{id}` | Atualiza o nome ou tipo de amostra da definição. |
| | `PATCH` | `/exam-definitions/{id}/deactivation` | Desativa logicamente a definição (impede novos pedidos). |
| **Ordens** | `POST` | `/exam-orders` | Cria uma nova ordem de exame (`status = RECEIVED`). |
| | `GET` | `/exam-orders` | Lista ordens de forma paginada (`page`, `size`, `sort`). |
| | `GET` | `/exam-orders/{id}` | Busca detalhes de uma ordem por UUID. |
| | `PUT` | `/exam-orders/{id}` | Atualiza dados da ordem (permitido apenas em `RECEIVED`). |
| | `DELETE` | `/exam-orders/{id}` | Remove a ordem (permitido apenas em `RECEIVED`). |
| **Laboratório** | `PATCH` | `/exam-orders/{id}/status` | Evolui o status operacional da ordem de exame. |
| | `POST` | `/exam-orders/{id}/result` | Registra o laudo clínico e marca a ordem como `RELEASED`. |
| | `GET` | `/exam-orders/{id}/result` | Consulta o laudo clínico liberado para a ordem. |
| **Notificações**| `GET` | `/notifications` | Lista notificações geradas paginadas (`patientId`, `page`, `size`). |

---

### Exemplos de Requisição e Resposta

#### 1. Cadastrar Paciente
```bash
curl -X POST http://localhost:8080/patients \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "Maria da Silva",
    "cpf": "52998224725",
    "birthDate": "1990-05-20"
  }'
```
**Resposta (`201 Created`):**
```json
{
  "id": "c3b88b02-53ef-4f1b-b27b-285640be4dc0",
  "fullName": "Maria da Silva",
  "cpf": "52998224725",
  "birthDate": "1990-05-20",
  "createdAt": "2026-11-12T15:00:00Z"
}
```

#### 2. Cadastrar Definição de Exame no Catálogo
```bash
curl -X POST http://localhost:8080/exam-definitions \
  -H "Content-Type: application/json" \
  -d '{
    "code": "HEMOGRAMA",
    "name": "Hemograma Completo",
    "sampleType": "BLOOD"
  }'
```
**Resposta (`201 Created`):**
```json
{
  "id": "e4f8b1a3-22dc-461d-a342-9a3b68c07e21",
  "code": "HEMOGRAMA",
  "name": "Hemograma Completo",
  "sampleType": "BLOOD",
  "active": true,
  "createdAt": "2026-11-12T15:05:00Z"
}
```

#### 3. Criar Ordem de Exame (Dispara Outbox ➡️ Kafka)
```bash
curl -X POST http://localhost:8080/exam-orders \
  -H "Content-Type: application/json" \
  -d '{
    "patientId": "c3b88b02-53ef-4f1b-b27b-285640be4dc0",
    "examCode": "HEMOGRAMA",
    "priority": "URGENT"
  }'
```
**Resposta (`201 Created`):**
```json
{
  "id": "f1d4a890-410a-4b92-9014-cb9287321fa1",
  "patientId": "c3b88b02-53ef-4f1b-b27b-285640be4dc0",
  "examCode": "HEMOGRAMA",
  "priority": "URGENT",
  "status": "RECEIVED",
  "createdAt": "2026-11-12T15:10:00Z",
  "updatedAt": "2026-11-12T15:10:00Z"
}
```
*(Após o consumo assíncrono pelo `laboratory-group`, o status passa automaticamente para `AWAITING_COLLECTION`)*.

#### 4. Evoluir Status Laboratorial
```bash
curl -X PATCH http://localhost:8080/exam-orders/f1d4a890-410a-4b92-9014-cb9287321fa1/status \
  -H "Content-Type: application/json" \
  -d '{
    "status": "COLLECTED"
  }'
```
**Resposta (`200 OK`):**
```json
{
  "id": "f1d4a890-410a-4b92-9014-cb9287321fa1",
  "patientId": "c3b88b02-53ef-4f1b-b27b-285640be4dc0",
  "examCode": "HEMOGRAMA",
  "priority": "URGENT",
  "status": "COLLECTED",
  "createdAt": "2026-11-12T15:10:00Z",
  "updatedAt": "2026-11-12T15:15:00Z"
}
```

#### 5. Registrar Laudo Clínico
```bash
curl -X POST http://localhost:8080/exam-orders/f1d4a890-410a-4b92-9014-cb9287321fa1/result \
  -H "Content-Type: application/json" \
  -d '{
    "resultText": "Hemoglobina: 14.2 g/dL. Plaquetas: 250.000 /mm³. Parâmetros dentro dos valores de referência."
  }'
```
**Resposta (`201 Created`):**
```json
{
  "id": "a90184b2-c12e-48a3-98fe-d790f4e19032",
  "orderId": "f1d4a890-410a-4b92-9014-cb9287321fa1",
  "patientId": "c3b88b02-53ef-4f1b-b27b-285640be4dc0",
  "examCode": "HEMOGRAMA",
  "resultText": "Hemoglobina: 14.2 g/dL. Plaquetas: 250.000 /mm³. Parâmetros dentro dos valores de referência.",
  "releasedAt": "2026-11-12T15:20:00Z"
}
```

#### 6. Consultar Notificações do Paciente
```bash
curl -X GET "http://localhost:8080/notifications?patientId=c3b88b02-53ef-4f1b-b27b-285640be4dc0"
```
**Resposta (`200 OK`):**
```json
{
  "content": [
    {
      "id": "7b091f32-8411-4f1a-b620-1a7629bca109",
      "orderId": "f1d4a890-410a-4b92-9014-cb9287321fa1",
      "patientId": "c3b88b02-53ef-4f1b-b27b-285640be4dc0",
      "examCode": "HEMOGRAMA",
      "status": "RELEASED",
      "message": "Seu exame HEMOGRAMA foi liberado! Acesse seu laudo pelo portal.",
      "createdAt": "2026-11-12T15:20:02Z"
    },
    {
      "id": "1198f420-72cb-48ad-81b2-8451873910c2",
      "orderId": "f1d4a890-410a-4b92-9014-cb9287321fa1",
      "patientId": "c3b88b02-53ef-4f1b-b27b-285640be4dc0",
      "examCode": "HEMOGRAMA",
      "status": "COLLECTED",
      "message": "Amostra do exame HEMOGRAMA coletada com sucesso.",
      "createdAt": "2026-11-12T15:15:02Z"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 10
  },
  "totalElements": 2,
  "totalPages": 1
}
```

---

## Tratamento de Erros (RFC 9457)

Todas as respostas de erro da aplicação seguem estritamente a especificação **RFC 9457** (`ProblemDetail`) com `Content-Type: application/problem+json`:

### Erro de Validação (`422 Unprocessable Content`)
```json
{
  "type": "urn:medflow:problem:validation-error",
  "title": "Dados inválidos",
  "status": 422,
  "detail": "Um ou mais campos estão inválidos.",
  "instance": "/patients",
  "errors": [
    {
      "message": "Nome completo deve ser informado",
      "pointer": "#/fullName"
    }
  ]
}
```

### Transição de Status Inválida (`409 Conflict`)
```json
{
  "type": "urn:medflow:problem:order-status-conflict",
  "title": "Transição de status inválida",
  "status": 409,
  "detail": "Ordem f1d4a890-410a-4b92-9014-cb9287321fa1 com status RELEASED não permite transição para CANCELLED.",
  "instance": "/exam-orders/f1d4a890-410a-4b92-9014-cb9287321fa1/status"
}
```

### Recurso Não Encontrado (`404 Not Found`)
```json
{
  "type": "urn:medflow:problem:exam-result-not-found",
  "title": "Resultado de exame não encontrado",
  "status": 404,
  "detail": "Resultado para a ordem f1d4a890-410a-4b92-9014-cb9287321fa1 não encontrado.",
  "instance": "/exam-orders/f1d4a890-410a-4b92-9014-cb9287321fa1/result"
}
```

---

## Testes

A aplicação conta com **140 testes automatizados**, cobrindo todas as camadas sem deixar pontas soltas:

- **Domínio puro:** Invariantes de entidade, validações de fábrica e máquina de status sem mocks (`ExamOrderTest`, `ExamResultTest`, `NotificationTest`, etc.).
- **Camada de Serviço:** Testes unitários com `@ExtendWith(MockitoExtension.class)` validando orquestração, regras transacionais e publicação na outbox.
- **Camada Web:** Testes com `@WebMvcTest` e `MockMvc` validando contratos HTTP, paginação e respostas RFC 9457.
- **Persistência com PostgreSQL real:** Testes de integração via Testcontainers validando as migrações do Flyway, constraints e chaves estrangeiras (`ExamOrderPersistenceIntegrationTest`, etc.).
- **E2E de Mensageria com Testcontainers:** O teste `ExamOrderMessagingIntegrationTest` sobe PostgreSQL e Apache Kafka reais, executando o ciclo de vida completo do exame (criação ➡️ outbox ➡️ Kafka ➡️ laboratório ➡️ evolução de status ➡️ laudo clínico ➡️ geração de notificações ➡️ deduplicação de reentrega).

### Como rodar a suíte completa:

```bash
cd medflow
./mvnw test
```

> **Nota para macOS / OrbStack:** Caso o ambiente utilize OrbStack para gerenciamento de containers, aponte a variável `DOCKER_HOST`:
> ```bash
> DOCKER_HOST=unix:///Users/$USER/.orbstack/run/docker.sock ./mvnw test
> ```

---

## Desafios Enfrentados

- **Transactional Outbox vs Chamada Direta ao Broker:** Disparar mensagens diretamente para o Kafka durante uma requisição HTTP introduz o problema de escrita dupla (*dual-write*): se o broker oscilar ou o commit do banco falhar, o estado fica corrompido. Persistir o evento na tabela `outbox_events` na mesma transação relacional garante que nenhum evento é criado sem o dado correspondente.
- **Deduplicação Idempotente por Grupo de Consumo:** Um mesmo evento Kafka precisa ser consumido por múltiplos módulos (ex.: `laboratory-group` para triagem e `notification-group` para avisos). Criar a chave primária composta `(event_id, consumer_group)` na tabela `processed_events` garantiu que cada módulo processe a mensagem uma única vez, descartando reentregas de forma independente.
- **Máquina de Estados e Imutabilidade do Laudo:** Ordens de exame possuem ciclo de vida complexo. Isolar a lógica de transição no método `changeStatus` da entidade `ExamOrder` com bloqueio para estados terminais (`RELEASED`, `REJECTED`, `CANCELLED`) e manter o laudo em tabela separada com relacionamento 1:1 evitou que laudos fossem sobrepostos indevidamente.
- **Paridade Estrita com Produção via Testcontainers:** Utilizar H2 em memória para testes de mensageria mascara incompatibilidades de dialeto SQL, transações PostgreSQL e comportamento real de partições do Kafka. A escolha por Testcontainers garantiu que a suíte execute contra o PostgreSQL 16 e Kafka 3.8.0 idênticos ao ambiente de execução.
- **Validação em Duas Camadas:** Bean Validation na camada web atua como barreira rápida para requisições malformadas, enquanto os métodos estáticos `validateData` no domínio impedem que qualquer fluxo interno construa entidades inválidas.

---

## Autor

**Vinícius Oliveira** · [GitHub](https://github.com/vineog23-boop) · [LinkedIn](https://www.linkedin.com/in/vinicius-oliveira-dev-)

---

## Licença

Este projeto é desenvolvido para fins de estudo, portfólio técnico e demonstração de arquitetura backend moderna em Java.
