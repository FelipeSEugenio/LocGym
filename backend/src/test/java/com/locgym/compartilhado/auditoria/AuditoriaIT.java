package com.locgym.compartilhado.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.locgym.TestcontainersConfig;
import com.locgym.TestcontainersConfig.BancoSuperusuario;
import com.locgym.compartilhado.tenancy.ContextoTenant;
import com.locgym.compartilhado.tenancy.DadosDeTeste;
import com.locgym.compartilhado.tenancy.Perfil;
import com.locgym.compartilhado.tenancy.TenantContext;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "locgym.outbox.relay-habilitado=false")
@Import(TestcontainersConfig.class)
class AuditoriaIT {

    @Autowired Auditoria auditoria;
    @Autowired TransactionTemplate tx;
    @Autowired BancoSuperusuario su;

    @AfterEach
    void limpar() {
        TenantContext.limpar();
    }

    @Test
    void exigeTransacao() {
        assertThatThrownBy(() -> auditoria.registrar(AcaoAuditada.PLANO_STATUS_ALTERADO, "plano",
                UUID.randomUUID(), null, null)).isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void gravaComAcademiaEUsuarioDoContexto() {
        UUID academia = DadosDeTeste.academia(su.jdbc());
        UUID usuario = DadosDeTeste.usuario(su.jdbc(), "auditor-" + UUID.randomUUID() + "@teste.com");
        UUID entidade = UUID.randomUUID();
        TenantContext.definir(new ContextoTenant(academia, Perfil.ADMIN, true, usuario));

        tx.executeWithoutResult(s -> auditoria.registrar(AcaoAuditada.PLANO_STATUS_ALTERADO, "plano", entidade,
                Map.of("status", "ATIVO"), Map.of("status", "INATIVO")));

        var linha = su.jdbc().queryForMap(
                "select academia_id, usuario_id, acao, entidade, antes->>'status' as antes, depois->>'status' as depois "
                        + "from auditoria where entidade_id = ?", entidade);
        assertThat(linha.get("academia_id")).isEqualTo(academia);
        assertThat(linha.get("usuario_id")).isEqualTo(usuario);
        assertThat(linha.get("acao")).isEqualTo("PLANO_STATUS_ALTERADO");
        assertThat(linha.get("entidade")).isEqualTo("plano");
        assertThat(linha.get("antes")).isEqualTo("ATIVO");
        assertThat(linha.get("depois")).isEqualTo("INATIVO");
    }

    @Test
    void semContextoNaoGrava() {
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> auditoria.registrar(
                AcaoAuditada.PLANO_STATUS_ALTERADO, "plano", UUID.randomUUID(), null, null)))
                .isInstanceOf(IllegalStateException.class);
    }
}
