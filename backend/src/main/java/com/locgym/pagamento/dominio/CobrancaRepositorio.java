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

    /** Lê bloqueando a linha até o fim da transação (pagamentos simultâneos da mesma cobrança se enfileiram). */
    Optional<Cobranca> porIdParaAtualizar(UUID id);

    /** Ordenadas por início do período. */
    List<Cobranca> daMatricula(UUID matriculaId);

    Optional<Cobranca> maisAntigaEmAberto(UUID matriculaId);

    /** Insere se ainda não há cobrança para esse início de período. @return {@code true} se inseriu */
    boolean inserirSeAusente(Cobranca cobranca);

    /** Em aberto (PENDENTE/ATRASADO), ordenadas pelo início do período. */
    List<Cobranca> emAbertoDaMatricula(UUID matriculaId);

    boolean temAtrasada(UUID matriculaId);

    boolean alunoTemAtrasada(UUID alunoId);

    Optional<LocalDate> vencimentoAtrasadoMaisAntigo(UUID matriculaId);

    Optional<LocalDate> vencimentoAtrasadoMaisAntigoDoAluno(UUID alunoId);

    Optional<LocalDate> maiorFimDePeriodo(UUID matriculaId);

    List<Cobranca> pendentesVencidas(LocalDate hoje, Set<UUID> matriculasIgnoradas);

    void salvar(Cobranca cobranca);

    /** Ordenada por vencimento. */
    Pagina<Cobranca> listar(FiltroCobrancas filtro, int pagina, int tamanho);
}
