# STATE

## Decisions

### AD-001
- **Decision**: Flyway será a fonte de verdade do schema PostgreSQL; Hibernate usará `ddl-auto=validate`.
- **Reason**: O schema precisa ser versionado, revisável e igual entre desenvolvimento, testes e futura produção.
- **Trade-off**: Toda mudança de persistência exigirá nova migration antes da aplicação iniciar.
- **Scope**: Entidades JPA, migrations e configuração de persistência.
- **Date**: 2026-09-16
- **Status**: active

### AD-002
- **Decision**: Testes de persistência usarão PostgreSQL real e descartável via Testcontainers, sem H2.
- **Reason**: O teste deve validar tipos, constraints, Flyway e comportamento reais do banco adotado pelo projeto.
- **Trade-off**: A suíte de integração exige Docker em execução e demora mais que testes unitários.
- **Scope**: Testes de integração que dependem de banco de dados.
- **Date**: 2026-09-16
- **Status**: active

## Handoff

- **Feature**: `.specs/features/persistencia-postgresql`
- **Phase / Task**: concluída e validada
- **Completed**: T1, T2, T3, T4, T5
- **In-progress** (file:line): none
- **Next step**: executar o fluxo manual no Postman com apoio guiado.
- **Blockers**: none
- **Validation**: PASS; 13/13 critérios, 92/92 testes e 3/3 mutações mortas.
- **Uncommitted files**: arquivos de saída e scripts didáticos já existentes, preservados.
- **Branch**: main
