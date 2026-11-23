package com.locgym.pagamento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Visão pública de uma cobrança (status e forma como nomes de enum). */
public record CobrancaResumo(UUID id, UUID matriculaId, UUID alunoId, BigDecimal valor, LocalDate vencimento,
                             LocalDate periodoInicio, LocalDate periodoFim, String status, LocalDate dataPagamento,
                             String forma, String observacoes, String motivoCancelamento) {

    public boolean emAberto() {
        return "PENDENTE".equals(status) || "ATRASADO".equals(status);
    }
}
