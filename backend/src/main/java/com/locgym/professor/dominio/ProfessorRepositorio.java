package com.locgym.professor.dominio;

import com.locgym.compartilhado.paginacao.Pagina;
import java.util.Optional;
import java.util.UUID;

public interface ProfessorRepositorio {

    /** Busca por trecho do nome ou e-mail (sem diferenciar maiúsculas), ordenada pelo nome. */
    Pagina<Professor> buscar(String buscaOuNulo, StatusProfessor statusOuNulo, int pagina, int tamanho);

    Optional<Professor> porId(UUID id);

    void salvar(Professor professor);
}
