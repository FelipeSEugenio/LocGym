package com.locgym.pagamento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** API pública das cobranças, usada pelo módulo de matrícula. Opera na academia do contexto; exige transação. */
public interface Cobrancas {

    /**
     * Gera a cobrança do período, idempotente: se já existe cobrança para esse início de período (em qualquer
     * status), devolve a existente sem publicar nada. Nasce ATRASADO se o vencimento já passou.
     */
    CobrancaResumo gerar(UUID matriculaId, UUID alunoId, BigDecimal valor, LocalDate periodoInicio, LocalDate periodoFim);

    /** Ordenadas pelo início do período. */
    List<CobrancaResumo> daMatricula(UUID matriculaId);

    boolean temAtrasada(UUID matriculaId);

    boolean temEmAberto(UUID matriculaId);

    /** Inadimplência é derivada: o aluno tem cobrança ATRASADA em qualquer matrícula (inclusive cancelada). */
    boolean alunoInadimplente(UUID alunoId);

    java.util.Optional<LocalDate> vencimentoAtrasadoMaisAntigo(UUID matriculaId);

    /** Início do primeiro período ainda sem cobrança: o maior fim de período já cobrado, ou {@code aPartirDe}. */
    LocalDate inicioDoProximoPeriodoSemCobranca(UUID matriculaId, LocalDate aPartirDe);

    /** PENDENTE vencida → ATRASADO, exceto de matrículas suspensas. @return quantas mudaram */
    int marcarAtrasadas(Set<UUID> matriculasSuspensas);

    /** Cancelamento da matrícula: PENDENTES → CANCELADO; ATRASADAS continuam devidas. */
    void cancelarPendentesDaMatricula(UUID matriculaId);

    /** Reativação: toda data de período posterior a {@code desde} anda {@code dias} (vencimento só de PENDENTE). */
    void deslocar(UUID matriculaId, LocalDate desde, int dias);

    /**
     * Abona, em ordem, as cobranças em aberto da matrícula da mais antiga até {@code ateCobrancaId} (inclusive).
     * Publica uma quitação por cobrança. @return ids abonados, em ordem
     */
    List<UUID> abonarAte(UUID matriculaId, UUID ateCobrancaId, String motivo);
}
