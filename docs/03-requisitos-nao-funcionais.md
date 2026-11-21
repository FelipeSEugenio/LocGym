# 03 — Requisitos não funcionais

| Requisito | Como é atendido | Status |
|---|---|---|
| **Isolamento por academia** | Coluna `academia_id` em todas as tabelas de tenant, filtro automático na aplicação e Row-Level Security no PostgreSQL; testes que tentam acessar dados de outra academia. | Fase 2 |
| **Segurança** | Senhas com BCrypt; JWT de acesso (15 min) + refresh token opaco rotacionado (7 dias); permissões por perfil; erros sem detalhes internos (Problem Details); segredos só por variáveis de ambiente; containers sem root. | Parcial (Fase 1: erros, segredos, containers) |
| **Controle de acesso por perfil** | Perfis ADMIN/ALUNO derivados dos vínculos com cada academia. | Fase 2 |
| **Auditoria** | Tabela `auditoria` (antes/depois em JSONB) para pagamentos, cancelamento/suspensão, correções de frequência e vínculos. | Fase 2+ |
| **Disponibilidade** | Health checks: **liveness** verifica só o processo; **readiness** verifica o banco, **não o Kafka** — a indisponibilidade do Kafka não tira o backend do ar, pois o outbox retém os eventos. | ✅ Fase 1 |
| **Confiabilidade de eventos** | Transactional outbox: evento só sai se a transação confirmar; retry automático; ordem preservada; entrega at-least-once. | ✅ Fase 1 |
| **Observabilidade** | Logs estruturados em JSON (ECS); métricas Micrometer/Prometheus em `/actuator/prometheus`; health em `/actuator/health`. | ✅ Fase 1 |
| **Escalabilidade** | Backend stateless; relay do outbox seguro com várias instâncias (advisory lock); Kafka particionado por academia. | ✅ base na Fase 1 |
| **Manutenibilidade** | Monolito modular (Spring Modulith) com Clean Architecture por módulo; regras verificadas por testes de arquitetura. | ✅ Fase 1 |
| **Testabilidade** | JUnit 5, Testcontainers (Postgres e Kafka reais), ArchUnit; Vitest no frontend; CI em todo push/PR. | ✅ Fase 1 |
| **Performance** | Índices por `academia_id`; paginação nas listagens; índice parcial para pendências do outbox. | Contínuo |
| **Backup e recuperação** | Volume nomeado do Postgres; `pg_dump` documentado para o ambiente local; estratégia gerenciada junto com o deploy (fase posterior). | Fase 6 |
| **Documentação e versionamento** | Markdown em `docs/`, ADRs em [09](09-decisoes-tecnicas.md), Conventional Commits, OpenAPI em `/swagger-ui.html`. | ✅ Fase 1 |
