-- Fase 4: grade semanal (horario), ocorrências (aula) e presença (checkin). Tabelas de tenant com RLS forçado.
-- Datas e horas são de negócio em America/Sao_Paulo.

create table horario (
    id            uuid primary key,
    academia_id   uuid        not null references academia (id),
    professor_id  uuid        not null references professor (id),
    dia_semana    smallint    not null check (dia_semana between 1 and 7),   -- ISO: 1 = segunda … 7 = domingo
    inicio        time(0)     not null,
    fim           time(0)     not null,
    tipo          varchar(60) not null check (btrim(tipo) <> ''),
    status        varchar(10) not null check (status in ('ATIVO', 'INATIVO')),
    version       bigint      not null default 0,
    criado_em     timestamptz not null default now(),
    atualizado_em timestamptz not null default now(),
    check (fim > inicio)   -- não cruza a meia-noite
);
create index ix_horario_academia_dia_status on horario (academia_id, dia_semana, status);

-- Ocorrência de um horário numa data, criada no primeiro check-in. Guarda uma cópia do horário:
-- alterar o horário depois não muda aulas já criadas (nem os relatórios).
create table aula (
    id           uuid primary key,
    academia_id  uuid        not null references academia (id),
    horario_id   uuid        not null references horario (id),
    data         date        not null,
    inicio       time(0)     not null,
    fim          time(0)     not null,
    professor_id uuid        not null references professor (id),
    tipo         varchar(60) not null,
    criado_em    timestamptz not null default now(),
    constraint ux_aula_horario_data unique (horario_id, data)
);
create index ix_aula_academia_data on aula (academia_id, data);

create table checkin (
    id              uuid primary key,
    academia_id     uuid         not null references academia (id),
    aluno_id        uuid         not null references aluno (id),
    aula_id         uuid references aula (id),          -- nulo = check-in livre (sem aula aberta)
    data            date         not null,
    hora            time(0)      not null,              -- truncada para minutos
    registrado_em   timestamptz  not null,
    origem          varchar(6)   not null check (origem in ('QR', 'MANUAL')),
    pontualidade    varchar(8)   check (pontualidade in ('PONTUAL', 'ATRASADO')),
    status          varchar(9)   not null check (status in ('ATIVO', 'CORRIGIDO', 'EXCLUIDO')),
    motivo          varchar(300),
    substituido_por uuid references checkin (id),
    registrado_por  uuid,
    version         bigint       not null default 0,
    criado_em       timestamptz  not null default now(),
    atualizado_em   timestamptz  not null default now(),
    check ((aula_id is null) = (pontualidade is null)),
    check (status = 'ATIVO' or motivo is not null),
    check ((status = 'CORRIGIDO') = (substituido_por is not null))
);
-- Um registro ativo por aluno em cada aula, e um check-in livre ativo por aluno por dia.
create unique index ux_checkin_aula_aluno on checkin (aula_id, aluno_id) where status = 'ATIVO' and aula_id is not null;
create unique index ux_checkin_livre_dia on checkin (academia_id, aluno_id, data) where status = 'ATIVO' and aula_id is null;
create index ix_checkin_academia_data on checkin (academia_id, data);
create index ix_checkin_academia_aluno_data on checkin (academia_id, aluno_id, data);

do $$
declare
    tabela text;
begin
    foreach tabela in array array['horario', 'aula', 'checkin'] loop
        execute format('alter table %I enable row level security', tabela);
        execute format('alter table %I force row level security', tabela);
        execute format($p$create policy tenant_isolamento on %I
                           using (academia_id = nullif(current_setting('app.academia_id', true), '')::uuid)
                           with check (academia_id = nullif(current_setting('app.academia_id', true), '')::uuid)$p$,
                       tabela);
    end loop;
end
$$;
