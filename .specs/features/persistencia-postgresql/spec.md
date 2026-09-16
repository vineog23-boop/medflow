# Persistência PostgreSQL e integração da ordem de exame

## Problem Statement

O MedFlow possui entidades JPA e repositories, mas não possui configuração de datasource nem migrations. Os testes atuais isolam as camadas com mocks e não comprovam que Hibernate, Flyway e PostgreSQL funcionam juntos. A criação de uma ordem também precisa expor erros HTTP coerentes quando o paciente ou a definição de exame não permitem a operação.

## Out of Scope

- Docker Compose ou empacotamento da aplicação.
- Configuração definitiva de Spring Security.
- Integração com RabbitMQ.
- OpenAPI/Swagger.
- Regra de unicidade de CPF.
- Alteração do modelo atual para associações JPA com `@ManyToOne`.
- Testes de integração HTTP completos; o Postman será feito manualmente depois.

## Assumptions & Open Questions

| Assumption | Chosen default | Rationale |
| --- | --- | --- |
| Banco da aplicação | PostgreSQL | Driver e Flyway PostgreSQL já fazem parte do projeto. |
| Controle do schema | Flyway cria; Hibernate valida | Evita geração implícita e divergência silenciosa. |
| Banco dos testes | PostgreSQL descartável via Testcontainers | Valida o mesmo mecanismo usado pela aplicação. |
| Exclusão de paciente com ordens | Bloqueada por chave estrangeira | Histórico clínico não deve ser apagado em cascata. |
| CPF | Não recebe unicidade nesta entrega | A regra ainda não existe na Service nem no contrato HTTP. |
| Credenciais | Somente variáveis de ambiente | Nenhum segredo será versionado. |

**Open questions: none**

## User Stories

### P1: Criar ordem somente com referências válidas

Como cliente da API, quero que uma ordem use um paciente existente e uma definição ativa para não persistir referências inválidas.

**Acceptance Criteria**:

1. WHEN uma criação ou atualização informa paciente inexistente, a aplicação SHALL rejeitar a operação com `PatientNotFoundException`.
2. WHEN uma criação ou atualização informa código de exame inexistente, a aplicação SHALL rejeitar a operação com `ExamDefinitionNotFoundException`.
3. WHEN uma criação ou atualização informa definição inativa, a aplicação SHALL rejeitar a operação com `ExamDefinitionInactiveException`.
4. WHEN o código informado varia apenas em caixa ou espaços, a aplicação SHALL persistir o código canônico da definição.

### P1: Expor falhas previsíveis no contrato HTTP

Como cliente da API, quero receber Problem Details estáveis para distinguir ausência, conflito de código e definição inativa.

**Acceptance Criteria**:

1. WHEN uma definição não existe, a API SHALL responder `404` com tipo `urn:medflow:problem:exam-definition-not-found`.
2. WHEN um código de definição já existe, a API SHALL responder `409` com tipo `urn:medflow:problem:exam-definition-code-conflict`.
3. WHEN uma definição está inativa, a API SHALL responder `409` com tipo `urn:medflow:problem:exam-definition-inactive`.

### P1: Persistir em PostgreSQL com schema versionado

Como desenvolvedor, quero schema Flyway e validação Hibernate para detectar incompatibilidades antes do uso da API.

**Acceptance Criteria**:

1. WHEN a aplicação inicia com PostgreSQL vazio, Flyway SHALL criar `patients`, `exam_definitions` e `exam_orders`.
2. WHEN Hibernate inicializa após Flyway, Hibernate SHALL validar com sucesso os mapeamentos das três entidades.
3. WHEN uma ordem referencia paciente ou definição inexistente, PostgreSQL SHALL rejeitar o registro por chave estrangeira.
4. WHEN uma ordem válida é criada pela `ExamOrderService`, a aplicação SHALL salvá-la e recuperá-la do PostgreSQL com paciente, código canônico, prioridade e status corretos.
5. WHEN a aplicação inicia fora dos testes, datasource SHALL obter URL, usuário e senha por variáveis de ambiente.

### P2: Documentar o estado executável

Como desenvolvedor, quero documentação coerente para executar e entender os testes sem confundir mock com integração real.

**Acceptance Criteria**:

1. WHEN o README descreve persistência e testes, o documento SHALL refletir migrations, Testcontainers, requisito de Docker e variáveis de ambiente reais.

## Requirement Traceability

| Requirement | Story | Status |
| --- | --- | --- |
| MEDP-01 | Criar ordem somente com referências válidas | Implementing |
| MEDP-02 | Expor falhas previsíveis no contrato HTTP | Pending |
| MEDP-03 | Persistir em PostgreSQL com schema versionado | Pending |
| MEDP-04 | Documentar o estado executável | Pending |
