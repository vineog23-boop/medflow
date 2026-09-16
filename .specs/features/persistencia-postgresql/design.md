# Design

## Fluxo de produção

```text
JSON -> Controller -> Service -> Repository JPA -> Hibernate -> PostgreSQL
                                              schema criado pelo Flyway
```

`ExamOrderService` valida a existência do paciente e busca a definição pelo código sem diferenciar caixa. A entidade `ExamOrder` recebe o código canônico. O banco reforça a integridade com duas chaves estrangeiras.

## Schema

- `patients`: UUID, nome, CPF, nascimento, timestamps e versão.
- `exam_definitions`: UUID, código único, nome, tipo de amostra, estado, timestamps e versão.
- `exam_orders`: UUID, paciente, código de exame, prioridade, status, timestamps e versão.
- `exam_orders.patient_id` referencia `patients.id`.
- `exam_orders.exam_code` referencia `exam_definitions.code`.
- Exclusões referenciadas usam comportamento restritivo padrão do PostgreSQL.

## Configuração

- `DB_URL`, `DB_USERNAME` e `DB_PASSWORD` alimentam o datasource.
- Flyway executa migrations antes do Hibernate.
- `spring.jpa.hibernate.ddl-auto=validate` impede criação automática de tabelas.
- `spring.jpa.open-in-view=false` evita sessão JPA aberta na camada web.

## Estratégia de teste

```text
Teste unitário: classe real + dependências mockadas
Teste de integração: Service + repositories + Hibernate + Flyway + PostgreSQL reais
```

Testcontainers inicia PostgreSQL descartável. `@ServiceConnection` fornece os dados da conexão ao Spring Boot. O teste verifica migration, persistência pela Service e constraints do banco. Não será usado H2.

## Tratamento de erros

Exceções de definição entram no `GlobalExceptionHandler` existente:

- ausente: `404 Not Found`;
- código duplicado: `409 Conflict`;
- inativa: `409 Conflict`.

## Tech Decisions

| Decision | Choice | Reason | Trade-off |
| --- | --- | --- | --- |
| Schema | Flyway + Hibernate validate | Versionamento explícito e detecção de divergência | Migration obrigatória para cada mudança |
| Banco de teste | Testcontainers PostgreSQL | Fidelidade ao banco real | Docker obrigatório e suíte mais lenta |
| Relações da ordem | Campos escalares + FK no banco | Preserva modelo atual e reforça integridade | JPA não navega diretamente entre entidades |
| Teste principal | `@SpringBootTest` focado em persistência | Exercita Service, JPA, Flyway e DB juntos | Contexto maior que `@DataJpaTest` |
