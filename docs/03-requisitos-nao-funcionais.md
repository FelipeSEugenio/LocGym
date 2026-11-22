# 03 — Requisitos não funcionais

| Requisito | Como é atendido | Status |
|---|---|---|
| **Isolamento por academia** | Duas camadas: Hibernate `@TenantId` grava e filtra `academia_id` em toda entidade de tenant, e **Row-Level Security forçado** no PostgreSQL com a academia definida por transação (`set_config('app.academia_id', …, true)`). A aplicação conecta como `locgym_app` (não é superusuário, não é dona das tabelas, `NOBYPASSRLS`). Testes provam que SQL nativo sem academia não vê nada e que cada endpoint devolve 404/403 para dados de outra academia. | ✅ Fase 2 |
| **Segurança** | Senhas com BCrypt (mín. 8 caracteres); login com resposta e tempo iguais para e-mail inexistente e senha errada; JWT RS256 de 15 min só em memória no navegador; refresh token de 7 dias em cookie `HttpOnly; Secure; SameSite=Strict`, guardado como hash, rotacionado a cada uso, com detecção de roubo por reuso (revoga a sessão); troca de senha obrigatória no primeiro acesso; erros sem detalhes internos; containers sem root; segredos por variável de ambiente. | ✅ Fase 2 |
| **Não revelar dados de outras academias** | Convites e cadastros de alunos respondem igual para e-mail novo ou existente; o nome mostrado do administrador é o informado no convite; recurso de outra academia responde 404, como inexistente. | ✅ Fase 2 |
| **Controle de acesso por perfil** | Perfis ADMIN/ALUNO derivados dos vínculos de cada academia; o contexto (academia + perfil) vem em headers e é conferido **no banco a cada requisição** — remover um admin ou cancelar um aluno vale imediatamente. | ✅ Fase 2 |
| **Auditoria** | Tabela `auditoria` (com RLS) grava usuário, ação, entidade e antes/depois em JSONB, na mesma transação, para: criação e status de academia, entrada e saída de administradores, status de aluno, professor e plano. Nunca grava senhas. | ✅ Fase 2 |
| **Disponibilidade** | Liveness verifica só o processo; readiness verifica o banco, **não o Kafka**; login e cadastros funcionam com o Kafka parado; o outbox retém eventos. | ✅ |
| **Confiabilidade de eventos** | Transactional outbox: atomicidade, retry, ordem preservada, at-least-once, timeouts curtos com o Kafka fora. | ✅ Fase 1 |
| **Observabilidade** | Logs estruturados (ECS); métricas Prometheus; health checks. | ✅ Fase 1 |
| **Escalabilidade** | Backend stateless (JWT); refresh com bloqueio de linha (renovações concorrentes serializadas); relay do outbox com advisory lock. | ✅ |
| **Manutenibilidade** | Monolito modular (Spring Modulith) com Clean Architecture por módulo — domínio puro, portas e adaptadores; regras verificadas por testes de arquitetura. | ✅ |
| **Testabilidade** | 123 testes no backend (JUnit 5, Testcontainers com Postgres e Kafka reais, rodando como `locgym_app`, ArchUnit) e 49 no frontend (Vitest); CI em todo push/PR. | ✅ |
| **Performance** | Índices por `academia_id` e para buscas por nome; paginação nas listagens. | Contínuo |
| **Backup e recuperação** | Volume nomeado do Postgres; estratégia gerenciada junto com o deploy. | Fase 6 |
| **Documentação e versionamento** | `docs/`, ADRs em [09](09-decisoes-tecnicas.md), Conventional Commits, OpenAPI. | ✅ |
