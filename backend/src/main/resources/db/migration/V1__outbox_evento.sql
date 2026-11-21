-- Transactional outbox: eventos de domínio gravados na mesma transação do negócio
-- e publicados no Kafka pelo OutboxRelay, na ordem de `sequencia`.
create table outbox_evento (
    id           uuid primary key,
    sequencia    bigint generated always as identity unique,
    tipo         varchar(100) not null,
    academia_id  uuid,
    versao       int          not null,
    payload      jsonb        not null,   -- envelope completo
    ocorrido_em  timestamptz  not null,
    publicado_em timestamptz,
    tentativas   int          not null default 0,
    ultimo_erro  text
);

create index ix_outbox_evento_pendente on outbox_evento (sequencia) where publicado_em is null;
