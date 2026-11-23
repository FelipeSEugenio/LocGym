package com.locgym.matricula.dominio;

import com.locgym.compartilhado.paginacao.Pagina;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Matrículas da academia ativa (o isolamento por academia é responsabilidade da infraestrutura). */
public interface MatriculaRepositorio {

    Optional<Matricula> porId(UUID id);

    Optional<Matricula> naoCanceladaDoAluno(UUID alunoId);

    List<Matricula> porStatus(StatusMatricula status);

    void salvar(Matricula matricula);

    void registrarPeriodo(UUID matriculaId, Periodo periodo, UUID cobrancaId);

    /** Desloca em {@code dias} os períodos com início em {@code desde} ou depois (reativação). */
    void deslocarPeriodosDesde(UUID matriculaId, LocalDate desde, int dias);

    /** Ordenados por início. */
    List<Periodo> periodos(UUID matriculaId);

    /** Busca por trecho do nome do aluno; ordenada pelo nome do aluno. */
    Pagina<Matricula> listar(StatusMatricula statusOuNulo, String buscaOuNulo, int pagina, int tamanho);
}
