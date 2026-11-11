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

- **Feature**: `.specs/features/fluxo-laboratorio-notificacoes`
- **Phase / Task**: concluída e validada
- **Completed**: T1, T2, T3, T4 + correções de cobertura e mutantes (`28e8e13`)
- **In-progress** (file:line): none
- **Next step**: revisar os commits na branch `main` ou publicar/atualizar o diagrama conforme necessidade.
- **Blockers**: none
- **Validation**: PASS (Round 2); 15/15 critérios, 130/130 testes e 8/8 mutações mortas (`validate_state.py`: 0 errors).
- **Uncommitted files**: arquivos auxiliares e saídas pré-existentes preservados.
- **Branch**: main
