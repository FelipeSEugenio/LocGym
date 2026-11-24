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

    private void dadosFinanceiros(UUID academia) {
        UUID usuario = DadosDeTeste.usuario(su.jdbc(), "rls-" + UUID.randomUUID() + "@teste.com");
        UUID aluno = DadosDeTeste.aluno(su.jdbc(), usuario, academia, "ATIVO");
        UUID plano = DadosDeTeste.plano(su.jdbc(), academia, "Plano " + UUID.randomUUID());
        var inicio = java.time.LocalDate.of(2026, 11, 1);
        UUID matricula = DadosDeTeste.matricula(su.jdbc(), academia, aluno, plano, inicio);
        UUID cobranca = DadosDeTeste.cobranca(su.jdbc(), academia, matricula, aluno, inicio);
        su.jdbc().update("""
                insert into matricula_periodo (id, academia_id, matricula_id, inicio, fim, cobranca_id)
                values (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), academia, matricula, inicio, inicio.plusMonths(1), cobranca);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"matricula", "matricula_periodo", "pagamento"})
    void tabelasDaFase3Isoladas(String tabela) {
        dadosFinanceiros(academiaA);
        dadosFinanceiros(academiaB);

        Integer semAcademia = tx.execute(s -> jdbc.queryForObject("select count(*) from " + tabela, Integer.class));
        var academias = comAcademia(academiaA,
                () -> jdbc.queryForList("select distinct academia_id from " + tabela, UUID.class));

        assertThat(semAcademia).isZero();
        assertThat(academias).containsExactly(academiaA);
    }

    private UUID dadosDeFrequencia(UUID academia) {
        UUID usuario = DadosDeTeste.usuario(su.jdbc(), "rls-" + UUID.randomUUID() + "@teste.com");
        UUID aluno = DadosDeTeste.aluno(su.jdbc(), usuario, academia, "ATIVO");
        UUID professor = DadosDeTeste.professor(su.jdbc(), academia, "ATIVO");
        UUID horario = DadosDeTeste.horario(su.jdbc(), academia, professor, 1,
                java.time.LocalTime.of(19, 0), java.time.LocalTime.of(20, 0), "Gi");
        var dia = java.time.LocalDate.of(2026, 11, 23);
        UUID aula = DadosDeTeste.aula(su.jdbc(), horario, dia);
        DadosDeTeste.checkin(su.jdbc(), academia, aluno, aula, dia, java.time.LocalTime.of(19, 5));
        return aluno;
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"horario", "aula", "checkin"})
    void tabelasDaFase4Isoladas(String tabela) {
        dadosDeFrequencia(academiaA);
        dadosDeFrequencia(academiaB);

        Integer semAcademia = tx.execute(s -> jdbc.queryForObject("select count(*) from " + tabela, Integer.class));
        var academias = comAcademia(academiaA,
                () -> jdbc.queryForList("select distinct academia_id from " + tabela, UUID.class));

        assertThat(semAcademia).isZero();
        assertThat(academias).containsExactly(academiaA);
    }

    @Test
    void naoInsereHorarioEmOutraAcademia() {
        UUID professor = DadosDeTeste.professor(su.jdbc(), academiaB, "ATIVO");

        assertThatThrownBy(() -> comAcademia(academiaA, () -> jdbc.update("""
                insert into horario (id, academia_id, professor_id, dia_semana, inicio, fim, tipo, status)
                values (?, ?, ?, 1, '19:00', '20:00', 'Gi', 'ATIVO')
                """, UUID.randomUUID(), academiaB, professor)))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void umCheckinAtivoPorAlunoNaAulaEUmLivrePorDia() {
        UUID aluno = dadosDeFrequencia(academiaA);
        UUID aula = su.jdbc().queryForObject(
                "select aula_id from checkin where aluno_id = ?", UUID.class, aluno);
        var dia = java.time.LocalDate.of(2026, 11, 23);

        assertThatThrownBy(() -> DadosDeTeste.checkin(su.jdbc(), academiaA, aluno, aula, dia, java.time.LocalTime.of(19, 6)))
                .isInstanceOf(DataAccessException.class);
        DadosDeTeste.checkin(su.jdbc(), academiaA, aluno, null, dia, java.time.LocalTime.of(10, 0));
        assertThatThrownBy(() -> DadosDeTeste.checkin(su.jdbc(), academiaA, aluno, null, dia, java.time.LocalTime.of(11, 0)))
                .isInstanceOf(DataAccessException.class);
        su.jdbc().update("update checkin set status = 'EXCLUIDO', motivo = 'erro' where aluno_id = ? and aula_id = ?", aluno, aula);
        DadosDeTeste.checkin(su.jdbc(), academiaA, aluno, aula, dia, java.time.LocalTime.of(19, 7));
    }

    @Test
    void horarioNaoCruzaMeiaNoite() {
        UUID professor = DadosDeTeste.professor(su.jdbc(), academiaA, "ATIVO");
        assertThatThrownBy(() -> DadosDeTeste.horario(su.jdbc(), academiaA, professor, 1,
                java.time.LocalTime.of(23, 0), java.time.LocalTime.of(1, 0), "Gi"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void naoInsereMatriculaEmOutraAcademia() {
        UUID usuario = DadosDeTeste.usuario(su.jdbc(), "rls-" + UUID.randomUUID() + "@teste.com");
        UUID aluno = DadosDeTeste.aluno(su.jdbc(), usuario, academiaB, "ATIVO");
        UUID plano = DadosDeTeste.plano(su.jdbc(), academiaB, "Plano invasor");

        assertThatThrownBy(() -> comAcademia(academiaA, () -> jdbc.update("""
                insert into matricula (id, academia_id, aluno_id, plano_id, valor, periodicidade, data_inicio,
                                       data_base, vigente_ate, status)
                values (?, ?, ?, ?, 10, 'MENSAL', current_date, current_date, current_date, 'PENDENTE')
                """, UUID.randomUUID(), academiaB, aluno, plano)))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void umaMatriculaNaoCanceladaPorAluno() {
        UUID usuario = DadosDeTeste.usuario(su.jdbc(), "rls-" + UUID.randomUUID() + "@teste.com");
        UUID aluno = DadosDeTeste.aluno(su.jdbc(), usuario, academiaA, "ATIVO");
        UUID plano = DadosDeTeste.plano(su.jdbc(), academiaA, "Plano unico");
        DadosDeTeste.matricula(su.jdbc(), academiaA, aluno, plano, java.time.LocalDate.of(2026, 11, 1));

        assertThatThrownBy(() -> DadosDeTeste.matricula(su.jdbc(), academiaA, aluno, plano,
                java.time.LocalDate.of(2026, 11, 2))).isInstanceOf(DataAccessException.class);
        su.jdbc().update("update matricula set status = 'CANCELADA', motivo = 'teste' where aluno_id = ?", aluno);
        DadosDeTeste.matricula(su.jdbc(), academiaA, aluno, plano, java.time.LocalDate.of(2026, 11, 3));
    }

    @Test
    void cobrancaPagaExigeDataEForma() {
        UUID usuario = DadosDeTeste.usuario(su.jdbc(), "rls-" + UUID.randomUUID() + "@teste.com");
        UUID aluno = DadosDeTeste.aluno(su.jdbc(), usuario, academiaA, "ATIVO");
        UUID plano = DadosDeTeste.plano(su.jdbc(), academiaA, "Plano pago");
        UUID matricula = DadosDeTeste.matricula(su.jdbc(), academiaA, aluno, plano, java.time.LocalDate.of(2026, 11, 1));
        UUID cobranca = DadosDeTeste.cobranca(su.jdbc(), academiaA, matricula, aluno, java.time.LocalDate.of(2026, 11, 1));

        assertThatThrownBy(() -> su.jdbc().update("update pagamento set status = 'PAGO' where id = ?", cobranca))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> DadosDeTeste.cobranca(su.jdbc(), academiaA, matricula, aluno,
                java.time.LocalDate.of(2026, 11, 1))).isInstanceOf(DataAccessException.class);
    }

    @Test
    void shedlockAcessivelAoPapelDaAplicacao() {
        jdbc.update("insert into shedlock (name, lock_until, locked_at, locked_by) values (?, now(), now(), 'teste')",
                "teste-" + UUID.randomUUID());
        assertThat(jdbc.queryForObject("select count(*) > 0 from shedlock", Boolean.class)).isTrue();
    }
}
