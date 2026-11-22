package com.locgym.compartilhado.auditoria.infraestrutura;

import com.locgym.compartilhado.auditoria.AcaoAuditada;
import com.locgym.compartilhado.auditoria.Auditoria;
import com.locgym.compartilhado.tenancy.ContextoTenant;
import com.locgym.compartilhado.tenancy.TenantContext;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Grava na tabela {@code auditoria} (protegida por RLS) dentro da transação do chamador. */
@Component
class AuditoriaJdbc implements Auditoria {

    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final Clock clock;

    AuditoriaJdbc(JdbcTemplate jdbc, JsonMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(AcaoAuditada acao, String entidade, UUID entidadeId, Object antes, Object depois) {
        ContextoTenant contexto = TenantContext.exigir();
        inserir(contexto.academiaId(), contexto.usuarioId(), acao, entidade, entidadeId, antes, depois);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarNaAcademia(UUID academiaId, UUID usuarioId, AcaoAuditada acao, String entidade,
                                    UUID entidadeId, Object antes, Object depois) {
        jdbc.queryForObject("select set_config('app.academia_id', ?, true)", String.class, academiaId.toString());
        inserir(academiaId, usuarioId, acao, entidade, entidadeId, antes, depois);
    }

    private void inserir(UUID academiaId, UUID usuarioId, AcaoAuditada acao, String entidade, UUID entidadeId,
                         Object antes, Object depois) {
        jdbc.update("""
                insert into auditoria (id, academia_id, usuario_id, acao, entidade, entidade_id, antes, depois, ocorrido_em)
                values (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)
                """, UUID.randomUUID(), academiaId, usuarioId, acao.name(), entidade, entidadeId,
                paraJson(antes), paraJson(depois), Timestamp.from(clock.instant()));
    }

    private String paraJson(Object valor) {
        return valor == null ? null : json.writeValueAsString(valor);
    }
}
