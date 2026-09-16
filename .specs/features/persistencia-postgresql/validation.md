# Validation persistencia-postgresql - PASS

**Date**: 2026-09-16  
**Spec**: `.specs/features/persistencia-postgresql/spec.md`  
**Diff range**: `091b3ab..8845e05`  
**Verifier**: independent sub-agent (author != verifier)  
**Result**: PASS

---

## Task Completion

| Task | Status | Notes |
| --- | --- | --- |
| T1 | Done | Referências de paciente e definição cobertas na criação e atualização. |
| T2 | Done | Três Problem Details verificados com status, tipo, título, detalhe e instance exatos. |
| T3 | Done | Dependências Testcontainers limitadas ao escopo `test`. |
| T4 | Done | Flyway, Hibernate, Service, repositories e duas FKs executados contra PostgreSQL 17 descartável. |
| T5 | Done | README conferido contra POM, properties, migration e suíte real. |

## Spec-Anchored Acceptance Criteria

| Critério | Resultado definido pela spec | Evidência `file:line` e asserção | Status |
| --- | --- | --- | --- |
| MEDP-01.1: criação ou atualização com paciente inexistente | `PatientNotFoundException` e nenhuma persistência | `medflow/src/test/java/br/com/medflow/exam/order/application/ExamOrderServiceTest.java:78` - `assertThatThrownBy(...create...).isInstanceOf(PatientNotFoundException.class)`; `.../ExamOrderServiceTest.java:198` - mesma asserção para `update`; `.../ExamOrderServiceTest.java:81` e `:201` - `verify(repository, never()).save(...)` | PASS |
| MEDP-01.2: criação ou atualização com código inexistente | `ExamDefinitionNotFoundException` e nenhuma persistência | `medflow/src/test/java/br/com/medflow/exam/order/application/ExamOrderServiceTest.java:96` e `:219` - `isInstanceOf(ExamDefinitionNotFoundException.class)`; `.../ExamOrderServiceTest.java:99` e `:222` - `never().save(...)` | PASS |
| MEDP-01.3: criação ou atualização com definição inativa | `ExamDefinitionInactiveException` e nenhuma persistência | `medflow/src/test/java/br/com/medflow/exam/order/application/ExamOrderServiceTest.java:116` e `:242` - `isInstanceOf(ExamDefinitionInactiveException.class)`; `.../ExamOrderServiceTest.java:119` e `:245` - `never().save(...)` | PASS |
| MEDP-01.4: código varia em caixa ou espaços | Persistir código canônico da definição | `medflow/src/test/java/br/com/medflow/exam/order/application/ExamOrderServiceTest.java:64` e `:180` - `assertThat(...examCode()).isEqualTo("HEMOGRAMA")` para create e update; entradas não canônicas em `:57` e `:168` | PASS |
| MEDP-02.1: definição inexistente | HTTP 404 e tipo `urn:medflow:problem:exam-definition-not-found` | `medflow/src/test/java/br/com/medflow/shared/web/error/GlobalExceptionHandlerTest.java:90` - `status().isNotFound()`; `.../GlobalExceptionHandlerTest.java:92` - `jsonPath("$.type").value("urn:medflow:problem:exam-definition-not-found")`; título, status, detalhe e instance em `:94`-`:99` | PASS |
| MEDP-02.2: código duplicado | HTTP 409 e tipo `urn:medflow:problem:exam-definition-code-conflict` | `medflow/src/test/java/br/com/medflow/shared/web/error/GlobalExceptionHandlerTest.java:105` - `status().isConflict()`; `.../GlobalExceptionHandlerTest.java:107` - tipo exato; título, status, detalhe e instance em `:109`-`:114` | PASS |
| MEDP-02.3: definição inativa | HTTP 409 e tipo `urn:medflow:problem:exam-definition-inactive` | `medflow/src/test/java/br/com/medflow/shared/web/error/GlobalExceptionHandlerTest.java:120` - `status().isConflict()`; `.../GlobalExceptionHandlerTest.java:122` - tipo exato; título, status, detalhe e instance em `:124`-`:129` | PASS |
| MEDP-03.1: PostgreSQL vazio | Flyway cria `patients`, `exam_definitions` e `exam_orders` na versão 1 | `medflow/src/test/java/br/com/medflow/persistence/MedflowPersistenceIntegrationTest.java:67` - versão igual a `1`; `.../MedflowPersistenceIntegrationTest.java:68` - `contains("patients", "exam_definitions", "exam_orders")` | PASS |
| MEDP-03.2: Hibernate inicia após Flyway | Mapeamentos das três entidades são validados com sucesso | `medflow/src/test/java/br/com/medflow/persistence/MedflowPersistenceIntegrationTest.java:33` - `@SpringBootTest` torna a criação do contexto uma asserção fail-fast; `medflow/src/main/resources/application.properties:5` fixa `ddl-auto=validate`; o gate iniciou Flyway v1 e depois o `EntityManagerFactory` sem erro | PASS |
| MEDP-03.3: referência inexistente | PostgreSQL rejeita paciente e definição ausentes por FK | `medflow/src/test/java/br/com/medflow/persistence/MedflowPersistenceIntegrationTest.java:113` e `:130` - `assertThatThrownBy(...saveAndFlush...).isInstanceOf(DataIntegrityViolationException.class)` | PASS |
| MEDP-03.4: ordem válida pela Service | Recuperar paciente, código canônico, prioridade e status corretos | `medflow/src/test/java/br/com/medflow/persistence/MedflowPersistenceIntegrationTest.java:94`-`:97` - igualdade de patient id, `HEMOGRAMA`, `URGENT` e `RECEIVED` após flush, clear e reload | PASS |
| MEDP-03.5: execução fora dos testes | URL, usuário e senha vêm de variáveis de ambiente | `medflow/src/main/resources/application.properties:2`-`:4` - configuração estática aponta exclusivamente para `${DB_URL}`, `${DB_USERNAME}` e `${DB_PASSWORD}`; `medflow/src/test/java/br/com/medflow/support/PostgreSqlTestContainerConfiguration.java:8`-`:14` mantém a substituição por container restrita a `@TestConfiguration` | PASS |
| MEDP-04.1: documentação executável | README reflete migrations, Testcontainers, Docker e variáveis reais | `README.md:62`-`:70` documenta Docker/Testcontainers; `README.md:74`-`:92` lista `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, Flyway e Hibernate; `README.md:217`-`:233` descreve a divisão e o resultado dos 92 testes | PASS |

**Status**: 13/13 ACs cobertos; 0 gaps de precisão da spec.

## Discrimination Sensor

| Mutação | Arquivo:linha | Falha injetada | Evidência de discriminação | Morta? |
| --- | --- | --- | --- | --- |
| 1 | `medflow/src/main/java/br/com/medflow/exam/order/application/ExamOrderService.java:43` | Create usa `request.examCode()` em vez do código canônico da definição | `ExamOrderServiceTest.java:64` falhou: esperado `HEMOGRAMA`, obtido `hemograma` | Sim |
| 2 | `medflow/src/main/java/br/com/medflow/shared/web/error/GlobalExceptionHandler.java:106` | Definição inativa retorna 404 em vez de 409 | `GlobalExceptionHandlerTest.java:120` falhou: esperado 409, obtido 404 | Sim |
| 3 | `medflow/src/main/resources/db/migration/V1__create_initial_schema.sql:31` | FK de paciente substituída por mero `CHECK NOT NULL` | `MedflowPersistenceIntegrationTest.java:113` falhou porque a inserção inválida deixou de lançar exceção | Sim |

**Sensor depth**: lightweight, três mutações de maior risco distribuídas entre Service, contrato HTTP e integridade relacional.  
**Result**: 3/3 mutações mortas - PASS.

O primeiro ciclo foi descartado porque um arquivo untracked externo apareceu no repositório real durante a comparação final. O ciclo foi repetido do zero com baseline atualizado. No ciclo válido, o porcelain real permaneceu idêntico antes e depois; os worktrees temporários foram removidos.

## Interactive UAT

Não aplicável. A feature é backend/infraestrutura e a spec exclui testes HTTP completos; os gates automatizados são suficientes para esta validação.

## Code Quality

| Princípio | Status | Evidência |
| --- | --- | --- |
| Mudança mínima e sem escopo extra | PASS | O diff implementa validação de referências, erros HTTP, persistência e documentação previstos. |
| Alterações cirúrgicas | PASS | 20 arquivos no diff, incluindo artefatos da spec; nenhum refactor adjacente foi introduzido. |
| Sem abstrações ou flexibilidade prematuras | PASS | Service reutiliza dois métodos privados; configuração de teste contém um único bean de PostgreSQL. |
| Padrões do projeto preservados | PASS | DTOs permanecem na borda, injeção por construtor, repositories Spring Data e Problem Details centralizados. |
| Resultados ancorados na spec | PASS | Asserções verificam exceções, status/tipos, código canônico, valores persistidos e FKs exatos. |
| Cobertura por camada | PASS | Service cobre create/update e falhas; handler cobre três contratos; integração cobre migration, bootstrap, persistência e duas FKs. |
| Testes novos ou alterados sem requisito | PASS | Todos os testes adicionados ou alterados no diff mapeiam a MEDP-01, MEDP-02 ou MEDP-03; testes preexistentes permanecem como regressão. |
| Diretrizes documentadas | PASS | Instruções globais `AGENTS.md` fornecidas no task e `tlc-spec-driven/references/coding-principles.md`. |
| Higiene do diff | PASS | `git diff --check 091b3ab..8845e05` sem saída; cinco commits atômicos alinhados a T1-T5. |

## Edge Cases

- [x] Caixa e espaços no código são normalizados pela busca e o código canônico é persistido.
- [x] Paciente inexistente é rejeitado em create e update.
- [x] Definição inexistente é rejeitada em create e update.
- [x] Definição inativa é rejeitada em create e update e exposta como HTTP 409.
- [x] PostgreSQL rejeita paciente inexistente pela FK.
- [x] PostgreSQL rejeita definição inexistente pela FK.
- [x] O teste usa PostgreSQL real descartável, sem H2 ou credenciais locais.

## Gate Check

- **Gate command**: `.\mvnw.cmd verify`, executado em `medflow/`.
- **Result**: 92 testes aprovados, 0 falhas, 0 erros, 0 ignorados; `BUILD SUCCESS`.
- **Infraestrutura observada**: Docker Desktop, Testcontainers 2.0.5, `postgres:17-alpine`, Flyway v1 e Hibernate ORM 7.4.5.Final.
- **Test count before feature**: 80 testes aprovados no commit `091b3ab`, medido com `verify` em worktree temporário.
- **Test count after feature**: 92.
- **Delta**: +12 testes.
- **Skipped tests**: nenhum.
- **Failures**: nenhuma no gate sem mutação.

## Requirement Traceability

O verifier não alterou `spec.md`, conforme a restrição de escrita desta validação. O estado verificado é:

| Requirement | Status anterior na spec | Estado verificado |
| --- | --- | --- |
| MEDP-01 | Implementing | Verified |
| MEDP-02 | Pending | Verified |
| MEDP-03 | Pending | Verified |
| MEDP-04 | Pending | Verified |

## Fix Plans

Nenhum. Não houve AC descoberto, gap de precisão nem mutante sobrevivente.

## Summary

**Overall**: Ready.  
**Spec-anchored check**: 13/13 ACs correspondem ao resultado definido pela spec; 0 gaps de precisão.  
**Sensor**: 3/3 mutações mortas.  
**Gate**: 92/92 testes aprovados.

PostgreSQL/Flyway/Hibernate funcionam juntos no teste de integração, a Service persiste somente referências válidas e canônicas, as duas FKs discriminam referências inválidas e os três Problem Details permanecem estáveis. A documentação coincide com o estado executável observado.
