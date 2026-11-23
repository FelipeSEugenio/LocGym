package com.locgym.matricula.dominio;

import static java.time.LocalDate.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.locgym.compartilhado.erros.RegraVioladaException;
import com.locgym.compartilhado.erros.ValorInvalidoException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MatriculaTest {

    static final LocalDate HOJE = of(2026, 11, 23);

    static Matricula nova(LocalDate inicio, LocalDate hoje) {
        return Matricula.nova(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("150.00"), "MENSAL", inicio, hoje);
    }

    /** Matrícula com vigência até {@code vigenteAte}, no status dado (âncora no mesmo dia do mês). */
    static Matricula existente(LocalDate vigenteAte, StatusMatricula status) {
        LocalDate base = vigenteAte.minusMonths(3);
        return new Matricula(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("150.00"),
                "MENSAL", base, base, vigenteAte, status, status == StatusMatricula.SUSPENSA ? vigenteAte.minusDays(5) : null,
                null);
    }

    static Periodo mes(LocalDate inicio) {
        return new Periodo(inicio, inicio.plusMonths(1));
    }

    static void erroDeRegra(Runnable acao, String codigo) {
        assertThatThrownBy(acao::run).isInstanceOfSatisfying(RegraVioladaException.class,
                e -> assertThat(e.codigo()).isEqualTo(codigo));
    }

    @Test
    void novaNascePendente() {
        Matricula m = nova(HOJE, HOJE);

        assertThat(m.status()).isEqualTo(StatusMatricula.PENDENTE);
        assertThat(m.dataInicio()).isEqualTo(HOJE);
        assertThat(m.dataBase()).isEqualTo(HOJE);
        assertThat(m.vigenteAte()).isEqualTo(HOJE);
        assertThat(m.primeiroPeriodo()).isEqualTo(mes(HOJE));
    }

    @Test
    void dataInicioForaDaJanela400() {
        for (LocalDate fora : new LocalDate[] {HOJE.minusDays(31), HOJE.plusDays(31)}) {
            assertThatThrownBy(() -> nova(fora, HOJE)).isInstanceOfSatisfying(ValorInvalidoException.class,
                    e -> assertThat(e.campo()).isEqualTo("dataInicio"));
        }
        assertThat(nova(HOJE.minusDays(30), HOJE).status()).isEqualTo(StatusMatricula.PENDENTE);
        assertThat(nova(HOJE.plusDays(30), HOJE).status()).isEqualTo(StatusMatricula.PENDENTE);
    }

    @Test
    void valorDeveSerPositivo() {
        assertThatThrownBy(() -> Matricula.nova(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ZERO, "MENSAL", HOJE, HOJE))
                .isInstanceOfSatisfying(ValorInvalidoException.class, e -> assertThat(e.campo()).isEqualTo("valor"));
    }

    @Test
    void primeiraQuitacaoAtiva() {
        Matricula m = nova(HOJE, HOJE);

        var q = m.aplicarQuitacao(m.primeiroPeriodo(), false, HOJE);

        assertThat(q.primeira()).isTrue();
        assertThat(q.ativou()).isTrue();
        assertThat(m.status()).isEqualTo(StatusMatricula.ATIVA);
        assertThat(m.vigenteAte()).isEqualTo(of(2026, 12, 23));
    }

    @Test
    void pagaAntesDoInicioFicaAtiva() {
        Matricula m = nova(HOJE.plusDays(10), HOJE);
        m.aplicarQuitacao(m.primeiroPeriodo(), false, HOJE);
        assertThat(m.status()).isEqualTo(StatusMatricula.ATIVA);
    }

    @Test
    void quitacaoForaDeSequenciaEhErroDeProgramacao() {
        Matricula m = nova(HOJE, HOJE);
        assertThatThrownBy(() -> m.aplicarQuitacao(mes(HOJE.plusMonths(1)), false, HOJE))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void renovacaoNaoEhPrimeira() {
        Matricula m = existente(of(2026, 12, 1), StatusMatricula.ATIVA);
        var q = m.aplicarQuitacao(mes(of(2026, 12, 1)), false, HOJE);
        assertThat(q.primeira()).isFalse();
        assertThat(q.ativou()).isFalse();
        assertThat(m.vigenteAte()).isEqualTo(of(2027, 1, 1));
    }

    @Test
    void vencidaComVigenciaNoPassadoContinuaVencida() {
        LocalDate hoje = of(2026, 11, 15);
        Matricula m = existente(of(2026, 8, 10), StatusMatricula.VENCIDA);

        var q = m.aplicarQuitacao(mes(of(2026, 8, 10)), false, hoje);

        assertThat(q.ativou()).isFalse();
        assertThat(m.status()).isEqualTo(StatusMatricula.VENCIDA);
        assertThat(m.vigenteAte()).isEqualTo(of(2026, 9, 10));
    }

    @Test
    void vencidaQuitadaAteHojeVoltaAtiva() {
        LocalDate hoje = of(2026, 11, 15);
        Matricula m = existente(of(2026, 11, 10), StatusMatricula.VENCIDA);

        var q = m.aplicarQuitacao(mes(of(2026, 11, 10)), false, hoje);

        assertThat(q.ativou()).isTrue();
        assertThat(m.status()).isEqualTo(StatusMatricula.ATIVA);
    }

    @Test
    void restandoAtrasadaNaoAtiva() {
        Matricula m = existente(of(2026, 11, 10), StatusMatricula.VENCIDA);
        m.aplicarQuitacao(mes(of(2026, 11, 10)), true, of(2026, 11, 15));
        assertThat(m.status()).isEqualTo(StatusMatricula.VENCIDA);
    }

    @Test
    void quitacaoEmCanceladaEhErroDeProgramacao() {
        Matricula m = existente(of(2026, 11, 10), StatusMatricula.CANCELADA);
        assertThatThrownBy(() -> m.aplicarQuitacao(mes(of(2026, 11, 10)), false, HOJE))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void quitacaoEmSuspensaAvancaVigenciaMantemStatus() {
        Matricula m = existente(of(2026, 11, 10), StatusMatricula.SUSPENSA);
        m.aplicarQuitacao(mes(of(2026, 11, 10)), false, HOJE);
        assertThat(m.status()).isEqualTo(StatusMatricula.SUSPENSA);
        assertThat(m.vigenteAte()).isEqualTo(of(2026, 12, 10));
    }

    @Test
    void periodosAGerarRecuperaMesesParados() {
        Matricula m = existente(of(2026, 9, 10), StatusMatricula.ATIVA);

        var periodos = m.periodosAGerar(of(2026, 9, 10), 7, of(2026, 11, 15));

        assertThat(periodos).extracting(Periodo::inicio)
                .containsExactly(of(2026, 9, 10), of(2026, 10, 10), of(2026, 11, 10));
    }

    @Test
    void periodosAGerarUmaSoNormalmente() {
        Matricula m = existente(of(2026, 12, 1), StatusMatricula.ATIVA);

        assertThat(m.periodosAGerar(of(2026, 12, 1), 7, of(2026, 11, 24))).containsExactly(mes(of(2026, 12, 1)));
        assertThat(m.periodosAGerar(of(2026, 12, 1), 7, of(2026, 11, 23))).isEmpty();
    }

    @Test
    void periodosAGerarAPartirDoJaCobrado() {
        Matricula m = existente(of(2026, 12, 1), StatusMatricula.ATIVA);
        assertThat(m.periodosAGerar(of(2027, 1, 1), 7, of(2026, 11, 24))).isEmpty();
    }

    @Test
    void suspenderEReativarDeslocaDatas() {
        Matricula m = existente(of(2026, 12, 1), StatusMatricula.ATIVA);
        LocalDate baseAntes = m.dataBase();

        m.suspender(" Viagem ", of(2026, 11, 1));
        assertThat(m.status()).isEqualTo(StatusMatricula.SUSPENSA);
        assertThat(m.suspensaDesde()).isEqualTo(of(2026, 11, 1));
        assertThat(m.motivo()).isEqualTo("Viagem");

        int dias = m.reativar(of(2026, 11, 13));

        assertThat(dias).isEqualTo(12);
        assertThat(m.status()).isEqualTo(StatusMatricula.ATIVA);
        assertThat(m.suspensaDesde()).isNull();
        assertThat(m.vigenteAte()).isEqualTo(of(2026, 12, 13));
        assertThat(m.dataBase()).isEqualTo(baseAntes.plusDays(12));
    }

    @Test
    void suspenderExigeMotivo() {
        Matricula m = existente(of(2026, 12, 1), StatusMatricula.ATIVA);
        assertThatThrownBy(() -> m.suspender(" ", HOJE)).isInstanceOf(ValorInvalidoException.class);
    }

    @Test
    void suspenderSoAtiva() {
        for (StatusMatricula s : new StatusMatricula[] {StatusMatricula.PENDENTE, StatusMatricula.VENCIDA,
                StatusMatricula.SUSPENSA, StatusMatricula.CANCELADA}) {
            erroDeRegra(() -> existente(of(2026, 12, 1), s).suspender("motivo", HOJE), "MATRICULA_NAO_ATIVA");
        }
    }

    @Test
    void reativarSoSuspensa() {
        erroDeRegra(() -> existente(of(2026, 12, 1), StatusMatricula.ATIVA).reativar(HOJE), "MATRICULA_NAO_SUSPENSA");
    }

    @Test
    void cancelarDeQualquerStatusUmaVez() {
        Matricula m = existente(of(2026, 12, 1), StatusMatricula.VENCIDA);
        m.cancelar(" Mudou de cidade ");
        assertThat(m.status()).isEqualTo(StatusMatricula.CANCELADA);
        assertThat(m.motivo()).isEqualTo("Mudou de cidade");
        erroDeRegra(() -> m.cancelar("de novo"), "MATRICULA_JA_CANCELADA");
    }

    @Test
    void cancelarSuspensaLimpaSuspensao() {
        Matricula m = existente(of(2026, 12, 1), StatusMatricula.SUSPENSA);
        m.cancelar("fim");
        assertThat(m.suspensaDesde()).isNull();
    }

    @Test
    void vencerSoAtiva() {
        Matricula ativa = existente(of(2026, 12, 1), StatusMatricula.ATIVA);
        Matricula pendente = existente(of(2026, 12, 1), StatusMatricula.PENDENTE);

        assertThat(ativa.vencerSe(false)).isFalse();
        assertThat(ativa.status()).isEqualTo(StatusMatricula.ATIVA);
        assertThat(ativa.vencerSe(true)).isTrue();
        assertThat(ativa.status()).isEqualTo(StatusMatricula.VENCIDA);
        assertThat(pendente.vencerSe(true)).isFalse();
        assertThat(pendente.status()).isEqualTo(StatusMatricula.PENDENTE);
    }
}
