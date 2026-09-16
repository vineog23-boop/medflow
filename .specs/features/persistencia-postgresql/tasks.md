# Tasks

## Test Coverage Matrix

| Requirement | Layer | Test evidence planned |
| --- | --- | --- |
| MEDP-01 | Service unit | paciente ausente, definição ausente/inativa e código canônico |
| MEDP-02 | Web unit | status, tipo, detalhe e instance dos três Problem Details |
| MEDP-03 | Integration | migration aplicada, Service persiste, FKs rejeitam referências inválidas |
| MEDP-04 | Documentation | revisão contra arquivos e comandos reais |

## Gate Check Commands

- **Quick**: `.\mvnw.cmd -Dtest=<classe> test`
- **Full**: `.\mvnw.cmd test`
- **Build**: `.\mvnw.cmd verify`

## Execution Plan

### Phase 1 - Consistência da aplicação

```text
T1 -> T2
```

### T1: Consolidar referências da ordem

**Depends on**: none
**Where**: `ExamOrderService.java`, repositories, exceções e `ExamOrderServiceTest.java`
**What**: Preservar e revisar o trabalho anterior que valida paciente e definição ativa, removendo somente ruído de formatação.
**Tests**: Testes unitários da `ExamOrderService`.
**Gate**: Quick
**Done when**: MEDP-01 possui casos felizes e falhas com assertions específicas; código canônico é salvo.

### T2: Mapear exceções de definição

**Depends on**: T1
**Where**: `GlobalExceptionHandler.java`, `ProblemTypes.java` e `GlobalExceptionHandlerTest.java`
**What**: Traduzir definição ausente, duplicada e inativa para Problem Details estáveis.
**Tests**: Testes web unitários escritos antes do handler.
**Gate**: Build
**Done when**: MEDP-02 retorna exatamente os status, tipos, títulos, detalhes e caminhos definidos.

### Phase 2 - Persistência real

```text
T3 -> T4
```

### T3: Adicionar suporte de teste com PostgreSQL descartável

**Depends on**: T2
**Where**: `pom.xml`
**What**: Adicionar `spring-boot-testcontainers` e `testcontainers-postgresql` somente no escopo de teste.
**Tests**: none; alteração de build autorizada pelo usuário.
**Gate**: Build
**Done when**: Maven resolve Testcontainers pela BOM do Spring Boot e o build existente continua verde.

### T4: Versionar schema e provar integração

**Depends on**: T3
**Where**: migration, `application.properties`, entidade do paciente e teste de integração.
**What**: Escrever o teste RED; criar migration e configuração mínimas; obter GREEN com PostgreSQL real.
**Tests**: Teste de integração com Service, repositories, Hibernate, Flyway e Testcontainers.
**Gate**: Build
**Done when**: MEDP-03 comprova migration, persistência completa e duas chaves estrangeiras.

### Phase 3 - Documentação

```text
T5
```

### T5: Atualizar documentação operacional

**Depends on**: T4
**Where**: `README.md`
**What**: Remover afirmações antigas e documentar variáveis, Flyway, Docker e divisão dos testes.
**Tests**: Revisão documental contra POM, properties, migration e saída da suíte.
**Gate**: Build
**Done when**: MEDP-04 descreve somente capacidades verificadas.

## Task Breakdown

- [x] T1 - Consolidar referências da ordem. Gate: 14 testes, 0 falhas.
- [x] T2 - Mapear exceções de definição. RED: 3 falhas por status 500; GREEN: 88 testes, 0 falhas.
- [ ] T3 - Adicionar suporte de teste com PostgreSQL descartável.
- [ ] T4 - Versionar schema e provar integração.
- [ ] T5 - Atualizar documentação operacional.
