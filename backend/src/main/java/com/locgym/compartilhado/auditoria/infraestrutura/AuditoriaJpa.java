package com.locgym.compartilhado.auditoria.infraestrutura;

import com.locgym.compartilhado.auditoria.AcaoAuditada;
import com.locgym.compartilhado.auditoria.Auditoria;
import com.locgym.compartilhado.tenancy.ContextoTenant;
import com.locgym.compartilhado.tenancy.TenantContext;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Component
class AuditoriaJpa implements Auditoria {

    private final RegistroAuditoriaDados dados;
    private final JsonMapper json;
    private final Clock clock;

    AuditoriaJpa(RegistroAuditoriaDados dados, JsonMapper json, Clock clock) {
        this.dados = dados;
        this.json = json;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(AcaoAuditada acao, String entidade, UUID entidadeId, Object antes, Object depois) {
        ContextoTenant contexto = TenantContext.exigir();
        dados.save(new RegistroAuditoria(contexto.usuarioId(), acao.name(), entidade, entidadeId,
                paraJson(antes), paraJson(depois), clock.instant()));
    }

    private String paraJson(Object valor) {
        return valor == null ? null : json.writeValueAsString(valor);
    }
}
