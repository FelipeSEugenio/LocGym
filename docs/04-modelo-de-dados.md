# 04 — Modelo de dados

Banco: **PostgreSQL 17**, migrations versionadas com **Flyway** (`backend/src/main/resources/db/migration`). Identificadores `uuid`; datas em `timestamptz`; regras de data de negócio no fuso `America/Sao_Paulo`.

## Implementado (Fase 1)

### `outbox_evento` — `V1__outbox_evento.sql`

| Coluna | Tipo | Descrição |
|---|---|---|
| `id` | uuid PK | Também é o `evento_id` do envelope. |
| `sequencia` | bigint identity, único | Ordem de gravação; o relay publica nessa ordem. |
| `tipo` | varchar(100) | Tipo do evento = nome do tópico Kafka. |
| `academia_id` | uuid, nulo | Tenant do evento; chave da mensagem Kafka. |
| `versao` | int | Versão do contrato do evento. |
| `payload` | jsonb | Envelope completo, exatamente como é publicado. |
| `ocorrido_em` | timestamptz | Momento em que o evento foi gerado. |
| `publicado_em` | timestamptz, nulo | Nulo enquanto pendente. |
| `tentativas` | int | Falhas de publicação acumuladas. |
| `ultimo_erro` | text | Última falha de publicação. |

Índice parcial `ix_outbox_evento_pendente (sequencia) where publicado_em is null`.

## Planejado (Fases 2–5)

Toda tabela de tenant tem `academia_id` (FK, indexado), `criado_em`, `atualizado_em` e, quando aplicável, `status`.

```mermaid
erDiagram
    USUARIO ||--o{ ADMINISTRADOR_ACADEMIA : "administra"
    ACADEMIA ||--o{ ADMINISTRADOR_ACADEMIA : ""
    USUARIO ||--o{ ALUNO : "login de"
    ACADEMIA ||--o{ ALUNO : ""
    ACADEMIA ||--o{ PROFESSOR : ""
    ACADEMIA ||--o{ PLANO : ""
    ALUNO ||--o{ MATRICULA : ""
    PLANO ||--o{ MATRICULA : ""
    MATRICULA ||--o{ MATRICULA_PERIODO : "vigências"
    MATRICULA ||--o{ PAGAMENTO : "cobranças"
    PROFESSOR ||--o{ HORARIO : "ministra"
    HORARIO ||--o{ AULA : "ocorrências"
    ALUNO ||--o{ CHECKIN : ""
    AULA |o--o{ CHECKIN : ""

    USUARIO {
        uuid id PK
        string email UK
        string senha_hash
    }
    ACADEMIA {
        uuid id PK
        string nome
        string cnpj
        string status
    }
    ALUNO {
        uuid id PK
        uuid academia_id FK
        uuid usuario_id FK
        string cpf
        string status
    }
    MATRICULA {
        uuid id PK
        uuid academia_id FK
        uuid aluno_id FK
        uuid plano_id FK
        date data_inicio
        date data_vencimento
        decimal valor
        string status
    }
    PAGAMENTO {
        uuid id PK
        uuid academia_id FK
        uuid matricula_id FK
        decimal valor
        date vencimento
        date data_pagamento
        string forma
        string status
    }
    HORARIO {
        uuid id PK
        uuid academia_id FK
        uuid professor_id FK
        int dia_semana
        time inicio
        time fim
        string tipo_aula
    }
    CHECKIN {
        uuid id PK
        uuid academia_id FK
        uuid aluno_id FK
        uuid aula_id FK
        timestamptz entrada_em
        string origem
        string pontualidade
        string status
    }
```

Restrições relevantes do design:
- `aluno`: `UNIQUE (academia_id, cpf)`.
- `matricula`: no máximo uma não cancelada por `(academia_id, aluno_id)` (índice único parcial).
- Tabelas de apoio previstas: `refresh_token`, `matricula_suspensao`, `notificacao`, `auditoria`, `configuracao_academia` (antecedência de cobrança, carência, tolerância de pontualidade).
