package com.locgym.pagamento.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.locgym.compartilhado.erros.RegraVioladaException;
import com.locgym.compartilhado.erros.ValorInvalidoException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CobrancaTest {

    static final LocalDate HOJE = LocalDate.of(2026, 11, 23);
    static final BigDecimal VALOR = new BigDecimal("150.00");

    static Cobranca nova(LocalDate inicio) {
        return Cobranca.nova(UUID.randomUUID(), UUID.randomUUID(), VALOR, inicio, inicio.plusMonths(1), HOJE);
    }

    @Test
    void novaComVencimentoFuturoNascePendente() {
        Cobranca c = nova(LocalDate.of(2026, 12, 1));

        assertThat(c.status()).isEqualTo(StatusCobranca.PENDENTE);
        assertThat(c.vencimento()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(c.periodoFim()).isEqualTo(LocalDate.of(2027, 1, 1));
    }

    @Test
    void novaComVencimentoHojeNascePendente() {
        assertThat(nova(HOJE).status()).isEqualTo(StatusCobranca.PENDENTE);
    }

    @Test
    void novaComVencimentoPassadoNasceAtrasada() {
        assertThat(nova(LocalDate.of(2026, 11, 22)).status()).isEqualTo(StatusCobranca.ATRASADO);
    }

    @Test
    void registrarPagamento() {
        Cobranca c = nova(HOJE);

        c.registrarPagamento(HOJE, FormaPagamento.PIX, "  pago no balcão ", HOJE);

        assertThat(c.status()).isEqualTo(StatusCobranca.PAGO);
        assertThat(c.dataPagamento()).isEqualTo(HOJE);
        assertThat(c.forma()).isEqualTo(FormaPagamento.PIX);
        assertThat(c.observacoes()).isEqualTo("pago no balcão");
    }

    @Test
    void observacaoEmBrancoViraNula() {
        Cobranca c = nova(HOJE);
        c.registrarPagamento(HOJE, FormaPagamento.DINHEIRO, "   ", HOJE);
        assertThat(c.observacoes()).isNull();
    }

    @Test
    void pagaCobrancaAtrasada() {
        Cobranca c = nova(LocalDate.of(2026, 10, 1));
        c.registrarPagamento(HOJE, FormaPagamento.DINHEIRO, null, HOJE);
        assertThat(c.status()).isEqualTo(StatusCobranca.PAGO);
    }

    @Test
    void dataPagamentoFutura400() {
        Cobranca c = nova(HOJE);
        assertThatThrownBy(() -> c.registrarPagamento(HOJE.plusDays(1), FormaPagamento.PIX, null, HOJE))
                .isInstanceOfSatisfying(ValorInvalidoException.class, e -> assertThat(e.campo()).isEqualTo("dataPagamento"));
    }

    @Test
    void pagamentoAntecipadoAceito() {
        Cobranca c = nova(LocalDate.of(2026, 12, 1));
        c.registrarPagamento(HOJE, FormaPagamento.CARTAO_CREDITO, null, HOJE);
        assertThat(c.status()).isEqualTo(StatusCobranca.PAGO);
    }

    @Test
    void formaObrigatoria() {
        Cobranca c = nova(HOJE);
        assertThatThrownBy(() -> c.registrarPagamento(HOJE, null, null, HOJE))
                .isInstanceOfSatisfying(ValorInvalidoException.class, e -> assertThat(e.campo()).isEqualTo("forma"));
    }

    @Test
    void naoPagaCobrancaPagaOuCancelada() {
        Cobranca paga = nova(HOJE);
        paga.registrarPagamento(HOJE, FormaPagamento.PIX, null, HOJE);
        Cobranca abonada = nova(HOJE);
        abonada.abonar("isenção");

        for (Cobranca c : new Cobranca[] {paga, abonada}) {
            assertThatThrownBy(() -> c.registrarPagamento(HOJE, FormaPagamento.PIX, null, HOJE))
                    .isInstanceOfSatisfying(RegraVioladaException.class,
                            e -> assertThat(e.codigo()).isEqualTo("COBRANCA_NAO_EM_ABERTO"));
            assertThatThrownBy(() -> c.abonar("de novo"))
                    .isInstanceOfSatisfying(RegraVioladaException.class,
                            e -> assertThat(e.codigo()).isEqualTo("COBRANCA_NAO_EM_ABERTO"));
        }
    }

    @Test
    void abonarExigeMotivo() {
        Cobranca c = nova(HOJE);
        assertThatThrownBy(() -> c.abonar("  "))
                .isInstanceOfSatisfying(ValorInvalidoException.class, e -> assertThat(e.campo()).isEqualTo("motivo"));
        assertThatThrownBy(() -> c.abonar("x".repeat(301)))
                .isInstanceOfSatisfying(ValorInvalidoException.class, e -> assertThat(e.campo()).isEqualTo("motivo"));
    }

    @Test
    void abonarAtrasada() {
        Cobranca c = nova(LocalDate.of(2026, 10, 1));

        c.abonar(" Aluno lesionado ");

        assertThat(c.status()).isEqualTo(StatusCobranca.CANCELADO);
        assertThat(c.motivoCancelamento()).isEqualTo("Aluno lesionado");
    }

    @Test
    void cancelarPorMatriculaPreservaAtrasada() {
        Cobranca pendente = nova(LocalDate.of(2026, 12, 1));
        Cobranca atrasada = nova(LocalDate.of(2026, 10, 1));

        assertThat(pendente.cancelarPorMatricula()).isTrue();
        assertThat(atrasada.cancelarPorMatricula()).isFalse();

        assertThat(pendente.status()).isEqualTo(StatusCobranca.CANCELADO);
        assertThat(pendente.motivoCancelamento()).isEqualTo("Matrícula cancelada");
        assertThat(atrasada.status()).isEqualTo(StatusCobranca.ATRASADO);
    }

    @Test
    void marcarAtrasada() {
        LocalDate vencimento = LocalDate.of(2026, 12, 1);
        Cobranca c = Cobranca.nova(UUID.randomUUID(), UUID.randomUUID(), VALOR, vencimento, vencimento.plusMonths(1), HOJE);

        assertThat(c.marcarAtrasadaSeVencida(vencimento)).isFalse();
        assertThat(c.status()).isEqualTo(StatusCobranca.PENDENTE);
        assertThat(c.marcarAtrasadaSeVencida(vencimento.plusDays(1))).isTrue();
        assertThat(c.status()).isEqualTo(StatusCobranca.ATRASADO);
        assertThat(c.marcarAtrasadaSeVencida(vencimento.plusDays(2))).isFalse();
    }

    @Test
    void deslocarPendenteInteiraDepoisDaSuspensao() {
        Cobranca pendente = nova(LocalDate.of(2026, 12, 1));
        pendente.deslocar(LocalDate.of(2026, 11, 1), 12);

        assertThat(pendente.vencimento()).isEqualTo(LocalDate.of(2026, 12, 13));
        assertThat(pendente.periodoInicio()).isEqualTo(LocalDate.of(2026, 12, 13));
        assertThat(pendente.periodoFim()).isEqualTo(LocalDate.of(2027, 1, 13));
    }

    @Test
    void deslocarAtrasadaSoEstendeOFimEMantemVencimento() {
        Cobranca atrasada = nova(LocalDate.of(2026, 11, 1));   // vencida antes da suspensão
        atrasada.deslocar(LocalDate.of(2026, 11, 5), 10);

        assertThat(atrasada.status()).isEqualTo(StatusCobranca.ATRASADO);
        assertThat(atrasada.vencimento()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(atrasada.periodoInicio()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(atrasada.periodoFim()).isEqualTo(LocalDate.of(2026, 12, 11));
    }

    @Test
    void deslocarPagaNaoMexeNoVencimento() {
        Cobranca paga = nova(LocalDate.of(2026, 12, 1));
        paga.registrarPagamento(HOJE, FormaPagamento.PIX, null, HOJE);
        paga.deslocar(LocalDate.of(2026, 11, 1), 12);

        assertThat(paga.vencimento()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(paga.periodoInicio()).isEqualTo(LocalDate.of(2026, 12, 13));
    }

    @Test
    void statusEmAberto() {
        assertThat(StatusCobranca.PENDENTE.emAberto()).isTrue();
        assertThat(StatusCobranca.ATRASADO.emAberto()).isTrue();
        assertThat(StatusCobranca.PAGO.emAberto()).isFalse();
        assertThat(StatusCobranca.CANCELADO.emAberto()).isFalse();
    }
}
