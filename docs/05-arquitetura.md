# 05 — Arquitetura

## Visão de containers

```mermaid
flowchart LR
    browser([Navegador]) -->|HTTP :4200| nginx[frontend<br/>nginx + SPA Angular]
    nginx -->|/api, /actuator/health| backend[backend<br/>Spring Boot 4 / Java 21]
    backend -->|JPA / Flyway| pg[(PostgreSQL 17)]
    backend -->|outbox relay| kafka[(Apache Kafka 4.3<br/>KRaft)]
    kafka -.->|consumidores<br/>(notificação, Fase 2+)| backend
```

- O navegador fala **só com o nginx**; o nginx faz proxy de `/api` e `/actuator/health` para o backend usando o DNS do Docker (re-resolvido a cada 10 s, sobrevive a reinícios do backend).
- O **frontend não depende do Kafka**; o backend fica *ready* sem o Kafka (ver [ADR-004](09-decisoes-tecnicas.md)).

## Backend: monolito modular

Um único deployable dividido em módulos de negócio verificados pelo **Spring Modulith** (`ApplicationModules.verify()` em `ArquiteturaTest`).

| Módulo | Responsabilidade | Fase |
|---|---|---|
| `compartilhado` | Infra técnica aberta a todos: eventos/outbox, tratamento de erros | ✅ 1 |
| `identidade` | Usuários, login, tokens, vínculos | 2 |
| `academia`, `aluno`, `professor`, `plano` | Cadastros | 2 |
| `matricula`, `pagamento` | Cobrança recorrente, inadimplência, job diário | 3 |
| `agenda`, `frequencia` | Horários, aulas, check-in | 4 |
| `relatorio`, `notificacao` | Indicadores; consumo de eventos | 5 |

### Camadas (Clean Architecture) dentro de cada módulo

```
com.locgym.<modulo>
├── dominio          entidades e regras — sem Spring, JPA ou Jackson
├── aplicacao        casos de uso e portas — não conhece infraestrutura nem web
├── infraestrutura   adaptadores: JPA, Kafka, agendamentos
└── web              controllers e DTOs REST
```

Regras verificadas por ArchUnit: `dominio` não depende de framework nem de outras camadas; `aplicacao` não depende de `infraestrutura` nem de `web`. Módulos conversam por API pública ou eventos — nunca pelos repositórios uns dos outros.

## Eventos

Transactional outbox → Kafka: detalhes em [07 — Eventos](07-eventos.md).

## Multi-tenancy (planejado — Fase 2)

- Schema único; `academia_id` em toda tabela de tenant.
- Tenant ativo vem do header `X-Academia-Id`, validado contra os vínculos do JWT (sem vínculo → 403).
- Duas camadas: filtro Hibernate automático na aplicação **e** Row-Level Security no PostgreSQL (`SET LOCAL app.academia_id` por transação; usuário de banco sem `BYPASSRLS`).
- Testes de isolamento tentam ler e escrever dados de outra academia em cada endpoint.

## Frontend

Angular 22 (standalone, signals), Angular Material, rotas com carregamento sob demanda:

| Rota | Conteúdo | Fase |
|---|---|---|
| `/` | Página inicial com status do backend | ✅ 1 |
| `/admin` | Shell do painel (toolbar + menu lateral) | esqueleto ✅ 1, módulos 2+ |
| `/aluno` | Shell mobile-first do portal | esqueleto ✅ 1, telas 5 |

## Infraestrutura

- `docker-compose.yml`: postgres, kafka, backend, frontend (+ `kafka-ui` no profile `tools`), com healthchecks e ordem de subida por `service_healthy`.
- Imagens multi-stage; backend roda como usuário não-root (uid 10001).
- CI (GitHub Actions): backend (`./mvnw verify`), frontend (lint, testes, build) e build das imagens.
