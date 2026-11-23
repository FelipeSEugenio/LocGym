package com.locgym.pagamento.aplicacao;

import com.locgym.compartilhado.eventos.EventoDominio;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Eventos publicados no Kafka (via outbox). Só ids, valores e datas — nunca dados pessoais. */
final class EventosPagamento {

    private EventosPagamento() {}

    record PagamentoGerado(UUID academiaId, UUID cobrancaId, UUID matriculaId, UUID alunoId, BigDecimal valor,
                           LocalDate vencimento) implements EventoDominio {
        public String tipo() { return "pagamento.gerado"; }
        public int versao() { return 1; }
    }

    record PagamentoRegistrado(UUID academiaId, UUID cobrancaId, UUID matriculaId, UUID alunoId, BigDecimal valor,
                               LocalDate dataPagamento, String forma) implements EventoDominio {
        public String tipo() { return "pagamento.registrado"; }
        public int versao() { return 1; }
    }

    record PagamentoAtrasado(UUID academiaId, UUID cobrancaId, UUID matriculaId, UUID alunoId, BigDecimal valor,
                             LocalDate vencimento) implements EventoDominio {
        public String tipo() { return "pagamento.atrasado"; }
        public int versao() { return 1; }
    }

    record PagamentoCancelado(UUID academiaId, UUID cobrancaId, UUID matriculaId, UUID alunoId, BigDecimal valor,
                              String motivo) implements EventoDominio {
        public String tipo() { return "pagamento.cancelado"; }
        public int versao() { return 1; }
    }
}
