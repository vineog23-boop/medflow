<h1 align="center">🩺 Medflow</h1>
<p align="center">Pacientes e ordens de exame com domínio, contratos HTTP e testes.</p>
<p align="center">
  <img src="https://img.shields.io/badge/Java-21-2563EB?style=flat-square" alt="Java: 21">
  <img src="https://img.shields.io/badge/Spring%20Boot-4.1.1-0F766E?style=flat-square" alt="Spring Boot: 4.1.1">
  <img src="https://img.shields.io/badge/Banco-PostgreSQL%20%2B%20Flyway-475569?style=flat-square" alt="Banco: PostgreSQL com Flyway">
  <img src="https://img.shields.io/badge/Status-Em%20desenvolvimento-475569?style=flat-square" alt="Status: Em desenvolvimento">
</p>

<p align="center"><a href="#visão-geral">Visão geral</a> · <a href="#funcionalidades">Funcionalidades</a> · <a href="#como-rodar">Execução</a> · <a href="#testes">Testes</a></p>

---

## Visão geral

Medflow é uma API REST para apoiar o fluxo de pacientes, catálogo laboratorial e ordens de exames. O checkpoint atual implementa os três recursos, persistência PostgreSQL versionada por Flyway, contratos HTTP validados e respostas de erro padronizadas em RFC 9457.

## Funcionalidades

- ✅ Cadastro, consulta paginada, busca por ID, atualização e remoção de pacientes;
- ✅ Validação de nome, CPF e data de nascimento do paciente;
- ✅ Cadastro, consulta, atualização e desativação de definições de exame;
- ✅ Cadastro, consulta paginada, busca por ID, atualização e remoção de ordens de exame;
- ✅ Ordem vinculada a paciente existente e definição de exame ativa;
- ✅ Prioridade `NORMAL` ou `URGENT` e status inicial `RECEIVED` para ordens de exame;
- ✅ DTOs na borda da API, sem expor entidades JPA diretamente;
- ✅ Erros HTTP padronizados para validação, UUID inválido, recurso não encontrado e falhas inesperadas;
- ✅ Schema PostgreSQL versionado por Flyway e validado pelo Hibernate;
- ✅ Testes unitários, web e de integração com PostgreSQL via Testcontainers;
- 🚧 Autenticação e autorização com Spring Security;
- 🚧 Mensageria com RabbitMQ;
- 🚧 Docker e documentação OpenAPI/Swagger.

## Tecnologias

| Tecnologia | Versão / uso | Descrição |
| --- | --- | --- |
| Java | 21 | Linguagem da aplicação. |
| Spring Boot | 4.1.1 | Base da aplicação e configuração do ecossistema Spring. |
| Spring Web MVC | Spring Boot Starter | API HTTP REST. |
| Spring Data JPA | Spring Boot Starter | Persistência e repositórios. |
| Hibernate | Via Spring Data JPA | Mapeamento objeto-relacional. |
| Bean Validation | Spring Boot Starter | Validação dos contratos de entrada. |
| PostgreSQL | Driver runtime | Banco relacional da aplicação e dos testes de integração. |
| Flyway | Spring Boot Starter | Criação e versionamento do schema. |
| Testcontainers | 2.0.5 via BOM | PostgreSQL real e descartável para testes de integração. |
| Spring Security | Spring Boot Starter | Dependência preparada; regras de segurança ainda não configuradas. |
| RabbitMQ / AMQP | Spring Boot Starter | Dependência preparada; fluxos de mensageria ainda não implementados. |
| Maven Wrapper | Incluído | Build e execução sem instalação global do Maven. |

## Como rodar

### Preparar o projeto

Com JDK 21 instalado, clone o repositório e entre no módulo Maven:

```bash
git clone https://github.com/vineog23-boop/Medflow.git
cd Medflow/medflow
```

Inicie o Docker Desktop e execute os testes. Testcontainers criará um PostgreSQL temporário:

```bash
bash ./mvnw test
```

No Windows: `.\mvnw.cmd test`.

Não é necessário criar banco nem informar credenciais para os testes. O container é descartado ao final da suíte.

### Executar a API

Crie um banco PostgreSQL vazio. Informe conexão e credenciais somente por variáveis de ambiente:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/medflow"
$env:DB_USERNAME = "seu_usuario"
$env:DB_PASSWORD = "sua_senha"
.\mvnw.cmd spring-boot:run
```

No Bash:

```bash
export DB_URL="jdbc:postgresql://localhost:5432/medflow"
export DB_USERNAME="seu_usuario"
export DB_PASSWORD="sua_senha"
./mvnw spring-boot:run
```

Na inicialização, Flyway aplica migrations pendentes. Depois, Hibernate valida se o schema corresponde às entidades. A aplicação não cria tabelas automaticamente.

Com um datasource válido, a aplicação usa a porta padrão `8080`.

### Com Docker

Ainda não há `compose.yaml` para executar a aplicação. Docker é usado somente pelos testes de integração via Testcontainers.

## Endpoints

Rotas implementadas nos controllers. Os exemplos abaixo descrevem os contratos e dependem da infraestrutura e das regras de acesso configuradas para uso real. Todos os dados dos exemplos são fictícios.

| Método | Endpoint | Descrição |
| --- | --- | --- |
| `GET` | `/patients` | Lista pacientes de forma paginada. Aceita `page`, `size` e `sort`. |
| `GET` | `/patients/{id}` | Busca um paciente pelo UUID. |
| `POST` | `/patients` | Cadastra um paciente. |
| `PUT` | `/patients/{id}` | Atualiza um paciente. |
| `DELETE` | `/patients/{id}` | Remove um paciente. |
| `GET` | `/exam-definitions` | Lista definições de exame. |
| `GET` | `/exam-definitions/{id}` | Busca uma definição pelo UUID. |
| `POST` | `/exam-definitions` | Cadastra uma definição de exame. |
| `PUT` | `/exam-definitions/{id}` | Atualiza nome e tipo de amostra. |
| `PATCH` | `/exam-definitions/{id}/deactivation` | Desativa uma definição sem removê-la. |
| `GET` | `/exam-orders` | Lista ordens de exame de forma paginada. Aceita `page`, `size` e `sort`. |
| `GET` | `/exam-orders/{id}` | Busca uma ordem de exame pelo UUID. |
| `POST` | `/exam-orders` | Cria uma ordem de exame. |
| `PUT` | `/exam-orders/{id}` | Atualiza uma ordem de exame. |
| `DELETE` | `/exam-orders/{id}` | Remove uma ordem de exame. |

### Cadastrar paciente

```bash
curl -X POST http://localhost:8080/patients \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "Maria da Silva",
    "cpf": "52998224725",
    "birthDate": "1990-05-20"
  }'
```

Resposta esperada (`201 Created`):

```json
{
  "id": "uuid-gerado-pela-aplicacao",
  "fullName": "Maria da Silva",
  "cpf": "52998224725",
  "birthDate": "1990-05-20",
  "createdAt": "2026-09-12T12:00:00Z"
}
```

### Criar ordem de exame

Substitua o `patientId` pelo UUID de um paciente já cadastrado.

```bash
curl -X POST http://localhost:8080/exam-orders \
  -H "Content-Type: application/json" \
  -d '{
    "patientId": "00000000-0000-0000-0000-000000000001",
    "examCode": "HEMOGRAMA",
    "priority": "URGENT"
  }'
```

Resposta esperada (`201 Created`):

```json
{
  "id": "uuid-gerado-pela-aplicacao",
  "patientId": "00000000-0000-0000-0000-000000000001",
  "examCode": "HEMOGRAMA",
  "priority": "URGENT",
  "status": "RECEIVED",
  "createdAt": "2026-09-12T12:00:00Z",
  "updatedAt": "2026-09-12T12:00:00Z"
}
```

## Tratamento de erros

A API usa `application/problem+json` e `ProblemDetail` para retornar erros. Por exemplo, um corpo inválido resulta em `422 Unprocessable Content` com o tipo `urn:medflow:problem:validation-error` e a lista de campos inválidos.

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

## Arquitetura

O projeto é organizado por funcionalidade. Cada módulo concentra domínio, aplicação, persistência e borda HTTP do seu contexto, enquanto os contratos de erro compartilhados ficam em `shared`.

```text
medflow/
├── src/main/java/br/com/medflow/
│   ├── MedflowApplication.java             # Inicialização da aplicação Spring Boot
│   ├── patient/
│   │   ├── application/                    # Casos de uso, DTOs e exceções de pacientes
│   │   ├── domain/                         # Entidade e regras de domínio de pacientes
│   │   ├── persistence/                    # Repositório JPA de pacientes
│   │   └── web/                            # Controller HTTP de pacientes
│   ├── exam/
│   │   ├── definition/                     # Catálogo de exames disponíveis
│   │   └── order/                          # Solicitações de exames por pacientes
│   └── shared/web/error/                   # RFC 9457, tipos de problema e handler global
├── src/main/resources/db/migration/         # Schema PostgreSQL versionado
└── src/test/java/br/com/medflow/            # Testes unitários, web e integração
```

## Testes

Os 92 testes atuais se dividem em:

- testes de domínio e DTO sem mocks;
- testes de Service com repositories mockados;
- testes web com `MockMvc` e Services mockadas;
- teste básico de inicialização do contexto;
- quatro testes de integração com Service, repositories, Hibernate, Flyway e PostgreSQL reais.

Execute a suíte a partir de `medflow/`, com Docker Desktop ativo:

```bash
bash ./mvnw test
```

No Windows: `.\mvnw.cmd test`.

O teste de integração comprova que a migration cria as três tabelas, a `ExamOrderService` persiste uma ordem completa e o PostgreSQL rejeita referências inexistentes pelas duas chaves estrangeiras.

## Decisões e desafios técnicos

- **Organização por funcionalidade:** `patient` e `exam` agrupam as responsabilidades do respectivo contexto, evitando pacotes globais por camada que misturam regras de negócio distintas.
- **DTOs como contrato de borda:** controllers recebem e retornam `record`s específicos; as entidades JPA não se tornam parte do contrato público da API.
- **Validação antes da regra de negócio:** Bean Validation protege os contratos HTTP, enquanto as entidades também validam seus invariantes para não dependerem exclusivamente da camada web.
- **Concorrência otimista:** as entidades possuem `@Version`, permitindo detectar atualizações concorrentes.
- **Erros previsíveis:** `GlobalExceptionHandler` centraliza falhas esperadas e inesperadas no formato RFC 9457, preservando controllers focados no fluxo HTTP normal.
- **Schema explícito:** Flyway cria o schema; `ddl-auto=validate` impede o Hibernate de modificar tabelas silenciosamente.
- **Banco real nos testes:** Testcontainers evita diferenças artificiais entre H2 e PostgreSQL.

## Roadmap

1. Validar manualmente os fluxos HTTP com Postman e PostgreSQL local.
2. Configurar autenticação e autorização com Spring Security.
3. Definir os eventos de mensageria e integrar RabbitMQ quando o caso de uso estiver estabelecido.
4. Adicionar Docker Compose e documentação OpenAPI/Swagger.

## Autor

**Vinícius Oliveira** · [GitHub](https://github.com/vineog23-boop) · [LinkedIn](https://www.linkedin.com/in/vinícius-oliveira-1770b7306)

## Licença

Este projeto ainda não possui uma licença de uso definida.
