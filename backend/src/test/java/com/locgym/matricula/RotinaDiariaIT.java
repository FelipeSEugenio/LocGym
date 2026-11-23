package com.locgym.matricula;

import static java.time.LocalDate.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.locgym.TestcontainersConfig;
import com.locgym.TestcontainersConfig.BancoSuperusuario;
import com.locgym.apoio.CalendarioDeTeste;
import com.locgym.compartilhado.tenancy.DadosDeTeste;
import com.locgym.compartilhado.tenancy.TenantContext;
import com.locgym.matricula.aplicacao.RotinaDiaria;
import com.locgym.pagamento.Cobrancas;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest(properties = {"locgym.outbox.relay-habilitado=false", "locgym.rotina-diaria.habilitada=false"})
@Import({TestcontainersConfig.class, CalendarioDeTeste.Config.class})
class RotinaDiariaIT {

    @Autowired RotinaDiaria rotina;
    @Autowired CalendarioDeTeste calendario;
    @Autowired BancoSuperusuario su;
    @Autowired JdbcTemplate jdbcApp;
    @Autowired MeterRegistry metricas;
    @Autowired org.springframework.transaction.support.TransactionTemplate tx;
    @MockitoSpyBean Cobrancas cobrancas;

    UUID academia;
    UUID aluno;
    UUID plano;

    @BeforeEach
    void preparar() {
        academia = DadosDeTeste.academia(su.jdbc());
        aluno = novoAluno(academia);
        plano = DadosDeTeste.plano(su.jdbc(), academia, "Mensal");
    }

    UUID novoAluno(UUID academia) {
        UUID usuario = DadosDeTeste.usuario(su.jdbc(), "rotina-" + UUID.randomUUID() + "@teste.com");
        return DadosDeTeste.aluno(su.jdbc(), usuario, academia, "ATIVO");
    }

    /** Matrícula ATIVA mensal vigente até {@code vigenteAte} (sem cobranças). */
    UUID matriculaAtiva(UUID academia, UUID aluno, UUID plano, LocalDate vigenteAte) {
        return DadosDeTeste.matricula(su.jdbc(), academia, aluno, plano, vigenteAte.minusMonths(1));
    }

    void executarEm(LocalDate dia) {
        calendario.definir(dia);
        rotina.executar();
    }

    List<String> cobrancas(UUID matricula, String coluna) {
        return su.jdbc().queryForList("select " + coluna + "::text from pagamento where matricula_id = ? order by periodo_inicio",
                String.class, matricula);
    }

    String statusMatricula(UUID matricula) {
        return su.jdbc().queryForObject("select status from matricula where id = ?", String.class, matricula);
    }

    int eventos(String tipo, UUID matricula) {
        return su.jdbc().queryForObject(
                "select count(*) from outbox_evento where tipo = ? and payload->'dados'->>'matriculaId' = ?",
                Integer.class, tipo, matricula.toString());
    }

    UUID cobrancaAtrasada(UUID matricula, LocalDate inicio) {
        UUID id = DadosDeTeste.cobranca(su.jdbc(), academia, matricula, aluno, inicio);
        su.jdbc().update("update pagamento set status = 'ATRASADO' where id = ?", id);
        return id;
    }

    @Test
    void geraSeteDiasAntes() {
        UUID m = matriculaAtiva(academia, aluno, plano, of(2026, 12, 1));

        executarEm(of(2026, 11, 23));
        assertThat(cobrancas(m, "vencimento")).isEmpty();

        executarEm(of(2026, 11, 24));
        assertThat(cobrancas(m, "vencimento")).containsExactly("2026-12-01");
        assertThat(cobrancas(m, "status")).containsExactly("PENDENTE");
        assertThat(eventos("pagamento.gerado", m)).isEqualTo(1);
    }

    @Test
    void rodarDuasVezesNaoDuplica() {
        UUID m = matriculaAtiva(academia, aluno, plano, of(2026, 12, 1));

        executarEm(of(2026, 11, 25));
        executarEm(of(2026, 11, 25));

        assertThat(cobrancas(m, "vencimento")).containsExactly("2026-12-01");
        assertThat(eventos("pagamento.gerado", m)).isEqualTo(1);
    }

    @Test
    void execucoesSimultaneasNaoDuplicam() {
        UUID m = matriculaAtiva(academia, aluno, plano, of(2026, 12, 1));
        calendario.definir(of(2026, 11, 25));

        var a = CompletableFuture.runAsync(rotina::executar);
        var b = CompletableFuture.runAsync(rotina::executar);
        a.join();
        b.join();

        assertThat(cobrancas(m, "vencimento")).containsExactly("2026-12-01");
        assertThat(eventos("pagamento.gerado", m)).isEqualTo(1);
    }

    @Test
    void atrasaNoDiaSeguinte() {
        UUID m = matriculaAtiva(academia, aluno, plano, of(2026, 12, 1));
        DadosDeTeste.cobranca(su.jdbc(), academia, m, aluno, of(2026, 12, 1));

        executarEm(of(2026, 12, 1));
        assertThat(cobrancas(m, "status")).containsExactly("PENDENTE");

        executarEm(of(2026, 12, 2));
        assertThat(cobrancas(m, "status")).containsExactly("ATRASADO");
        assertThat(su.jdbc().queryForObject(
                "select count(*) from outbox_evento where tipo = 'pagamento.atrasado' and payload->'dados'->>'matriculaId' = ?",
                Integer.class, m.toString())).isEqualTo(1);
    }

    @Test
    void venceAposCarencia() {
        UUID m = matriculaAtiva(academia, aluno, plano, of(2026, 12, 1));
        cobrancaAtrasada(m, of(2026, 12, 1));

        executarEm(of(2026, 12, 11));
        assertThat(statusMatricula(m)).isEqualTo("ATIVA");

        executarEm(of(2026, 12, 12));
        assertThat(statusMatricula(m)).isEqualTo("VENCIDA");
        assertThat(eventos("matricula.vencida", m)).isEqualTo(1);
    }

    @Test
    void respeitaParametrosDaAcademia() {
        UUID outra = DadosDeTeste.academia(su.jdbc());
        su.jdbc().update("update academia set dias_antecedencia_cobranca = 3, dias_carencia = 0 where id = ?", outra);
        UUID alunoOutra = novoAluno(outra);
        UUID planoOutra = DadosDeTeste.plano(su.jdbc(), outra, "Mensal");
        UUID padrao = matriculaAtiva(academia, aluno, plano, of(2026, 12, 1));
        UUID curta = matriculaAtiva(outra, alunoOutra, planoOutra, of(2026, 12, 1));

        executarEm(of(2026, 11, 24));

        assertThat(cobrancas(padrao, "vencimento")).hasSize(1);
        assertThat(cobrancas(curta, "vencimento")).isEmpty();

        su.jdbc().update("update pagamento set status = 'ATRASADO' where matricula_id in (?, ?)", padrao, curta);
        DadosDeTeste.cobranca(su.jdbc(), outra, curta, alunoOutra, of(2026, 12, 1));
        su.jdbc().update("update pagamento set status = 'ATRASADO' where matricula_id = ?", curta);
        executarEm(of(2026, 12, 2));

        assertThat(statusMatricula(padrao)).isEqualTo("ATIVA");
        assertThat(statusMatricula(curta)).isEqualTo("VENCIDA");
    }

    @Test
    void ignoraSuspensaEAcademiaInativa() {
        UUID suspensa = matriculaAtiva(academia, aluno, plano, of(2026, 12, 1));
        DadosDeTeste.cobranca(su.jdbc(), academia, suspensa, aluno, of(2026, 12, 1));
        su.jdbc().update("update matricula set status = 'SUSPENSA', suspensa_desde = date '2026-11-20' where id = ?", suspensa);
        UUID inativa = DadosDeTeste.academia(su.jdbc(), "INATIVA");
        UUID alunoInativa = novoAluno(inativa);
        UUID daInativa = matriculaAtiva(inativa, alunoInativa, DadosDeTeste.plano(su.jdbc(), inativa, "Mensal"), of(2026, 12, 1));

        executarEm(of(2026, 12, 20));

        assertThat(cobrancas(suspensa, "status")).containsExactly("PENDENTE");
        assertThat(statusMatricula(suspensa)).isEqualTo("SUSPENSA");
        assertThat(cobrancas(daInativa, "status")).isEmpty();
    }

    @Test
    void pendenteNaoVence() {
        UUID m = matriculaAtiva(academia, aluno, plano, of(2026, 12, 1));
        su.jdbc().update("update matricula set status = 'PENDENTE' where id = ?", m);
        cobrancaAtrasada(m, of(2026, 11, 1));

        executarEm(of(2026, 12, 30));

        assertThat(statusMatricula(m)).isEqualTo("PENDENTE");
    }

    @Test
    void planoInativadoNaoAfetaCobranca() {
        UUID m = matriculaAtiva(academia, aluno, plano, of(2026, 12, 1));
        su.jdbc().update("update plano set status = 'INATIVO', valor = 999 where id = ?", plano);

        executarEm(of(2026, 11, 25));

        assertThat(cobrancas(m, "valor")).containsExactly("150.00");
    }

    @Test
    void alunoInativoContinuaSendoCobrado() {
        UUID m = matriculaAtiva(academia, aluno, plano, of(2026, 12, 1));
        su.jdbc().update("update aluno set status = 'INATIVO' where id = ?", aluno);

        executarEm(of(2026, 11, 25));

        assertThat(cobrancas(m, "vencimento")).containsExactly("2026-12-01");
    }

    @Test
    void academiaReativadaRecuperaPeriodos() {
        UUID m = matriculaAtiva(academia, aluno, plano, of(2026, 8, 1));

        executarEm(of(2026, 11, 15));

        assertThat(cobrancas(m, "vencimento")).containsExactly("2026-08-01", "2026-09-01", "2026-10-01", "2026-11-01");
        assertThat(cobrancas(m, "status")).containsOnly("ATRASADO");
        assertThat(statusMatricula(m)).isEqualTo("VENCIDA");
    }

    @Test
    void falhaNumaAcademiaNaoParaAsOutras() {
        UUID outra = DadosDeTeste.academia(su.jdbc());
        UUID alunoOutra = novoAluno(outra);
        UUID daOutra = matriculaAtiva(outra, alunoOutra, DadosDeTeste.plano(su.jdbc(), outra, "Mensal"), of(2026, 12, 1));
        UUID daQuebrada = matriculaAtiva(academia, aluno, plano, of(2026, 12, 1));
        double antes = falhasRegistradas(academia);
        stubbing(() -> doAnswer(inv -> {
            if (TenantContext.exigir().academiaId().equals(academia)) {
                throw new IllegalStateException("falha simulada");
            }
            return inv.callRealMethod();
        }).when(cobrancas).marcarAtrasadas(any()));

        calendario.definir(of(2026, 11, 25));
        var resultado = rotina.executar();

        assertThat(resultado.falhas()).isGreaterThanOrEqualTo(1);
        assertThat(cobrancas(daOutra, "vencimento")).containsExactly("2026-12-01");
        assertThat(cobrancas(daQuebrada, "vencimento")).isEmpty();   // transação da academia desfeita
        assertThat(falhasRegistradas(academia)).isEqualTo(antes + 1);
    }

    @Test
    void cadaAcademiaSoVeAsProprias() {
        matriculaAtiva(academia, aluno, plano, of(2026, 12, 1));
        AtomicReference<List<UUID>> vistas = new AtomicReference<>();
        stubbing(() -> doAnswer(inv -> {
            if (TenantContext.exigir().academiaId().equals(academia)) {
                vistas.set(jdbcApp.queryForList("select distinct academia_id from matricula", UUID.class));
            }
            return inv.callRealMethod();
        }).when(cobrancas).marcarAtrasadas(any()));

        executarEm(of(2026, 11, 25));

        assertThat(vistas.get()).containsExactly(academia);
    }

    /** O spy fica dentro do proxy transacional (propagação MANDATORY): o stubbing precisa de uma transação. */
    void stubbing(Runnable r) {
        tx.executeWithoutResult(s -> r.run());
    }

    double falhasRegistradas(UUID academia) {
        var contador = metricas.find("locgym.rotina_diaria.falhas").tag("academia", academia.toString()).counter();
        return contador == null ? 0 : contador.count();
    }
}
