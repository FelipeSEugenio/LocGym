-- Fase 3: matrículas, períodos de vigência e cobranças (pagamentos). Tabelas de tenant com RLS forçado.
-- Datas são datas de negócio em America/Sao_Paulo; períodos são [inicio, fim) — fim exclusivo.

create table matricula (
    id             uuid primary key,
    academia_id    uuid           not null references academia (id),
    aluno_id       uuid           not null references aluno (id),
    plano_id       uuid           not null references plano (id),
    valor          numeric(10, 2) not null check (valor > 0),
    periodicidade  varchar(12)    not null check (periodicidade in ('MENSAL', 'TRIMESTRAL', 'SEMESTRAL', 'ANUAL')),
    data_inicio    date           not null,
    data_base      date           not null,   -- âncora do calendário de períodos (desloca com suspensões)
    vigente_ate    date           not null,   -- primeiro dia NÃO coberto (= vencimento exibido)
    status         varchar(10)    not null check (status in ('PENDENTE', 'ATIVA', 'VENCIDA', 'SUSPENSA', 'CANCELADA')),
    suspensa_desde date,
    motivo         varchar(300),
    version        bigint         not null default 0,
    criado_em      timestamptz    not null default now(),
    atualizado_em  timestamptz    not null default now(),
    check ((status = 'SUSPENSA') = (suspensa_desde is not null))
);
-- No máximo uma matrícula não cancelada por aluno em cada academia.
create unique index ux_matricula_aluno_nao_cancelada on matricula (academia_id, aluno_id) where status <> 'CANCELADA';
create index ix_matricula_academia_status on matricula (academia_id, status);

create table pagamento (
    id                  uuid primary key,
    academia_id         uuid           not null references academia (id),
    matricula_id        uuid           not null references matricula (id),
    aluno_id            uuid           not null references aluno (id),
    valor               numeric(10, 2) not null check (valor > 0),
    vencimento          date           not null,
    periodo_inicio      date           not null,
    periodo_fim         date           not null,
    status              varchar(10)    not null check (status in ('PENDENTE', 'PAGO', 'ATRASADO', 'CANCELADO')),
    data_pagamento      date,
    forma               varchar(14)    check (forma in ('DINHEIRO', 'PIX', 'CARTAO_CREDITO', 'CARTAO_DEBITO', 'TRANSFERENCIA')),
    observacoes         varchar(500),
    motivo_cancelamento varchar(300),
    version             bigint         not null default 0,
    criado_em           timestamptz    not null default now(),
    atualizado_em       timestamptz    not null default now(),
    check (periodo_fim > periodo_inicio),
    check ((status = 'PAGO') = (data_pagamento is not null and forma is not null)),
    check ((status = 'CANCELADO') = (motivo_cancelamento is not null)),
    -- Uma cobrança por período, para sempre (inclusive canceladas): torna a geração idempotente.
    constraint ux_pagamento_matricula_periodo unique (matricula_id, periodo_inicio)
);
create index ix_pagamento_academia_status_vencimento on pagamento (academia_id, status, vencimento);
create index ix_pagamento_academia_aluno on pagamento (academia_id, aluno_id);

create table matricula_periodo (
    id           uuid primary key,
    academia_id  uuid        not null references academia (id),
    matricula_id uuid        not null references matricula (id),
    inicio       date        not null,
    fim          date        not null,
    cobranca_id  uuid        not null references pagamento (id),
    criado_em    timestamptz not null default now(),
    check (fim > inicio),
    -- deferrable: deslocar todos os períodos de uma vez (reativação) colidiria linha a linha.
    constraint ux_matricula_periodo_inicio unique (matricula_id, inicio) deferrable initially deferred
);

do $$
declare
    tabela text;
begin
    foreach tabela in array array['matricula', 'pagamento', 'matricula_periodo'] loop
        execute format('alter table %I enable row level security', tabela);
        execute format('alter table %I force row level security', tabela);
        execute format($p$create policy tenant_isolamento on %I
                           using (academia_id = nullif(current_setting('app.academia_id', true), '')::uuid)
                           with check (academia_id = nullif(current_setting('app.academia_id', true), '')::uuid)$p$,
                       tabela);
    end loop;
end
$$;

-- ShedLock: tabela técnica global (sem RLS) para a rotina diária rodar em uma instância por vez.
create table shedlock (
    name       varchar(64)  primary key,
    lock_until timestamp    not null,
    locked_at  timestamp    not null,
    locked_by  varchar(255) not null
);
grant select, insert, update on shedlock to ${app_usuario};
