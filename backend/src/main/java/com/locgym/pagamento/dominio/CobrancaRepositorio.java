package com.locgym.pagamento.dominio;

import com.locgym.compartilhado.paginacao.Pagina;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Cobranças da academia ativa (o isolamento por academia é responsabilidade da infraestrutura). */
public interface CobrancaRepositorio {

    record FiltroCobrancas(StatusCobranca status, LocalDate vencimentoDe, LocalDate vencimentoAte, UUID alunoId) {}

    Optional<Cobranca> porId(UUID id);

    /** Ordenadas por início do período. */
    List<Cobranca> daMatricula(UUID matriculaId);

    Optional<Cobranca> maisAntigaEmAberto(UUID matriculaId);

    boolean existePeriodo(UUID matriculaId, LocalDate periodoInicio);

    List<Cobranca> pendentesVencidas(LocalDate hoje, Set<UUID> matriculasIgnoradas);

    void salvar(Cobranca cobranca);

    /** Ordenada por vencimento. */
    Pagina<Cobranca> listar(FiltroCobrancas filtro, int pagina, int tamanho);
}
