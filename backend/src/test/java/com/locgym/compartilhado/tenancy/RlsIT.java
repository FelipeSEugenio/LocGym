package com.locgym.compartilhado.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.locgym.TestcontainersConfig;
import com.locgym.TestcontainersConfig.BancoSuperusuario;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** RLS no PostgreSQL, testado com a conexão real da aplicação ({@code locgym_app}). */
@SpringBootTest(properties = "locgym.outbox.relay-habilitado=false")
@Import(TestcontainersConfig.class)
class RlsIT {

    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired BancoSuperusuario su;

    UUID academiaA;
    UUID academiaB;

    @BeforeEach
    void preparar() {
        academiaA = DadosDeTeste.academia(su.jdbc());
        academiaB = DadosDeTeste.academia(su.jdbc());
        DadosDeTeste.plano(su.jdbc(), academiaA, "Mensal A");
        DadosDeTeste.plano(su.jdbc(), academiaB, "Mensal B");
    }

    private <T> T comAcademia(UUID academia, java.util.function.Supplier<T> acao) {
        return tx.execute(s -> {
            jdbc.queryForObject("select set_config('app.academia_id', ?, true)", String.class, academia.toString());
            return acao.get();
        });
    }

    @Test
    void aplicacaoNaoESuperusuarioNemIgnoraRls() {
        var papel = jdbc.queryForMap(
                "select current_user as usuario, rolsuper, rolbypassrls from pg_roles where rolname = current_user");
        assertThat(papel.get("usuario")).isEqualTo("locgym_app");
        assertThat(papel.get("rolsuper")).isEqualTo(false);
        assertThat(papel.get("rolbypassrls")).isEqualTo(false);
    }

    @Test
    void semAcademiaDefinidaNaoVeNada() {
        Integer total = tx.execute(s -> jdbc.queryForObject("select count(*) from plano", Integer.class));
        assertThat(total).isZero();
    }

    @Test
    void comAcademiaDefinidaVeSoAsSuas() {
        var academias = comAcademia(academiaA,
                () -> jdbc.queryForList("select distinct academia_id from plano", UUID.class));
        assertThat(academias).containsExactly(academiaA);
    }

    @Test
    void naoInsereEmOutraAcademia() {
        assertThatThrownBy(() -> comAcademia(academiaA, () -> jdbc.update(
                "insert into plano (id, academia_id, nome, valor, periodicidade, status) values (?, ?, 'Invasor', 10, 'MENSAL', 'ATIVO')",
                UUID.randomUUID(), academiaB)))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void vinculosIgnoramAlunoCancelado() {
        UUID academiaC = DadosDeTeste.academia(su.jdbc());
        UUID usuario = DadosDeTeste.usuario(su.jdbc(), "vinculos-" + UUID.randomUUID() + "@teste.com");
        DadosDeTeste.administrador(su.jdbc(), usuario, academiaA);
        DadosDeTeste.aluno(su.jdbc(), usuario, academiaB, "ATIVO");
        DadosDeTeste.aluno(su.jdbc(), usuario, academiaC, "CANCELADO");

        var vinculos = jdbc.queryForList(
                "select academia_id, academia_nome, academia_status, perfil from vinculos_do_usuario(?)", usuario);

        assertThat(vinculos).extracting(v -> v.get("academia_id") + "/" + v.get("perfil"))
                .containsExactlyInAnyOrder(academiaA + "/ADMIN", academiaB + "/ALUNO");
        assertThat(vinculos).allSatisfy(v -> {
            assertThat(v.get("academia_nome")).isNotNull();
            assertThat(v.get("academia_status")).isEqualTo("ATIVA");
        });
    }

    @Test
    void outboxContinuaSemRls() {
        assertThat(jdbc.queryForObject("select count(*) >= 0 from outbox_evento", Boolean.class)).isTrue();
    }
}
