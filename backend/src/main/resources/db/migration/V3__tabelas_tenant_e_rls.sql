-- Tabelas de tenant: academia_id obrigatório + Row-Level Security forçado.
-- A academia ativa é definida por transação: select set_config('app.academia_id', <uuid>, true).
-- Sem academia definida, nenhuma linha é visível e nenhum INSERT é aceito.

create table aluno (
    id              uuid primary key,
    academia_id     uuid         not null references academia (id),
    usuario_id      uuid         not null references usuario (id),
    nome            varchar(150) not null check (btrim(nome) <> ''),
    cpf             char(11)     not null check (cpf ~ '^[0-9]{11}$'),
    telefone        varchar(20)  not null,
    data_nascimento date         not null,
    endereco        varchar(200) not null,
    cidade          varchar(100) not null,
    estado          char(2)      not null check (estado ~ '^[A-Z]{2}$'),
    cep             char(8)      not null check (cep ~ '^[0-9]{8}$'),
    status          varchar(10)  not null check (status in ('ATIVO', 'INATIVO', 'CANCELADO')),
    criado_em       timestamptz  not null default now(),
    atualizado_em   timestamptz  not null default now(),
    unique (academia_id, cpf),
    unique (academia_id, usuario_id)
);
create index ix_aluno_usuario on aluno (usuario_id);
create index ix_aluno_academia_nome on aluno (academia_id, lower(nome));

create table professor (
    id              uuid primary key,
    academia_id     uuid         not null references academia (id),
    nome            varchar(150) not null check (btrim(nome) <> ''),
    email           varchar(254),
    telefone        varchar(20),
    data_nascimento date,
    especialidade   varchar(100),
    status          varchar(10)  not null check (status in ('ATIVO', 'INATIVO')),
    criado_em       timestamptz  not null default now(),
    atualizado_em   timestamptz  not null default now()
);
create index ix_professor_academia_nome on professor (academia_id, lower(nome));

create table plano (
    id            uuid primary key,
    academia_id   uuid          not null references academia (id),
    nome          varchar(100)  not null check (btrim(nome) <> ''),
    descricao     varchar(500),
    valor         numeric(10, 2) not null check (valor > 0),
    periodicidade varchar(12)   not null check (periodicidade in ('MENSAL', 'TRIMESTRAL', 'SEMESTRAL', 'ANUAL')),
    status        varchar(10)   not null check (status in ('ATIVO', 'INATIVO')),
    criado_em     timestamptz   not null default now(),
    atualizado_em timestamptz   not null default now(),
    unique (academia_id, nome)
);

create table auditoria (
    id          uuid primary key,
    academia_id uuid        not null references academia (id),
    usuario_id  uuid        references usuario (id),
    acao        varchar(50) not null,
    entidade    varchar(50) not null,
    entidade_id uuid        not null,
    antes       jsonb,
    depois      jsonb,
    ocorrido_em timestamptz not null default now()
);
create index ix_auditoria_academia_data on auditoria (academia_id, ocorrido_em);

do $$
declare
    tabela text;
begin
    foreach tabela in array array['aluno', 'professor', 'plano', 'auditoria'] loop
        execute format('alter table %I enable row level security', tabela);
        execute format('alter table %I force row level security', tabela);
        execute format($p$create policy tenant_isolamento on %I
                           using (academia_id = nullif(current_setting('app.academia_id', true), '')::uuid)
                           with check (academia_id = nullif(current_setting('app.academia_id', true), '')::uuid)$p$,
                       tabela);
    end loop;
end
$$;
