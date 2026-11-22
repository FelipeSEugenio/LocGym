-- Papel da aplicação: não é superusuário, não é dono das tabelas e não ignora RLS.
-- As migrations rodam com o dono do schema; a aplicação conecta como ${app_usuario}.
do $$
begin
    if not exists (select from pg_roles where rolname = '${app_usuario}') then
        execute format('create role %I login password %L nosuperuser nocreatedb nocreaterole nobypassrls',
                       '${app_usuario}', '${app_senha}');
    end if;
end
$$;

grant usage on schema public to ${app_usuario};
alter default privileges in schema public grant select, insert, update, delete on tables to ${app_usuario};
alter default privileges in schema public grant usage, select on sequences to ${app_usuario};
grant select, insert, update, delete on outbox_evento to ${app_usuario};
grant usage, select on all sequences in schema public to ${app_usuario};
grant select on flyway_schema_history to ${app_usuario};

-- ===== Tabelas globais (sem RLS; acesso controlado pela aplicação) =====

create table usuario (
    id                uuid primary key,
    email             varchar(254) not null unique check (email = lower(btrim(email))),
    nome              varchar(150) not null check (btrim(nome) <> ''),
    senha_hash        varchar(100) not null,
    deve_trocar_senha boolean      not null default false,
    criado_em         timestamptz  not null default now(),
    atualizado_em     timestamptz  not null default now()
);

create table academia (
    id            uuid primary key,
    nome          varchar(150) not null check (btrim(nome) <> ''),
    cnpj          char(14)     not null unique check (cnpj ~ '^[0-9]{14}$'),
    endereco      varchar(200) not null,
    cidade        varchar(100) not null,
    estado        char(2)      not null check (estado ~ '^[A-Z]{2}$'),
    cep           char(8)      not null check (cep ~ '^[0-9]{8}$'),
    telefone      varchar(20)  not null,
    email         varchar(254) not null,
    status        varchar(10)  not null check (status in ('ATIVA', 'INATIVA')),
    criado_em     timestamptz  not null default now(),
    atualizado_em timestamptz  not null default now()
);

create table administrador_academia (
    usuario_id  uuid         not null references usuario (id),
    academia_id uuid         not null references academia (id),
    nome        varchar(150) not null check (btrim(nome) <> ''),  -- nome informado para esta academia
    criado_em   timestamptz  not null default now(),
    primary key (usuario_id, academia_id)
);
create index ix_administrador_academia_academia on administrador_academia (academia_id);

create table refresh_token (
    id          uuid primary key,
    usuario_id  uuid        not null references usuario (id),
    token_hash  char(64)    not null unique,   -- SHA-256 em hex; o valor bruto nunca é gravado
    familia     uuid        not null,
    expira_em   timestamptz not null,
    usado_em    timestamptz,
    revogado_em timestamptz,
    criado_em   timestamptz not null default now()
);
create index ix_refresh_token_familia on refresh_token (familia);
create index ix_refresh_token_usuario on refresh_token (usuario_id);
