# Contexto

## Estado confirmado

- Java 21 e Spring Boot 4.1.1.
- JPA, PostgreSQL e Flyway já declarados no `pom.xml`.
- Nenhuma migration ou configuração de datasource existente.
- 83 testes verdes antes desta entrega.
- Testes de Service usam repositories mockados.
- Testes web usam `MockMvc` standalone e Services mockadas.
- `MedflowApplicationTests` exclui DataSource, JPA, Flyway e repositories reais.
- Testcontainers não estava presente.
- Docker CLI está instalado; Docker Desktop não estava em execução na inspeção inicial.

## Código em andamento preservado

O working tree já contém alterações funcionais não commitadas que fazem `ExamOrderService` consultar `PatientRepository` e `ExamDefinitionRepository`. Há testes unitários correspondentes. Alterações meramente acidentais de espaços serão removidas sem alterar comportamento.

## Limites

O modelo mantém UUID e código de exame como campos escalares em `ExamOrder`. As chaves estrangeiras serão aplicadas no PostgreSQL, sem introduzir associações JPA nesta entrega.

## Ideias adiadas

- Unicidade e normalização persistente de CPF.
- Tratamento HTTP específico para tentativa de excluir paciente referenciado.
- Teste HTTP completo com segurança configurada.
- Docker Compose para execução local da aplicação.
