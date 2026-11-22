package com.locgym.aluno.dominio;

import com.locgym.compartilhado.paginacao.Pagina;
import java.util.Optional;
import java.util.UUID;

/** Alunos da academia ativa (o isolamento por academia é responsabilidade da infraestrutura). */
public interface AlunoRepositorio {

    /** Busca por trecho do nome, do e-mail ou do CPF, ordenada pelo nome. */
    Pagina<Aluno> buscar(String buscaOuNulo, StatusAluno statusOuNulo, int pagina, int tamanho);

    Optional<Aluno> porId(UUID id);

    Optional<Aluno> porUsuario(UUID usuarioId);

    boolean cpfEmUso(Cpf cpf, UUID excetoAluno);

    boolean usuarioJaEAluno(UUID usuarioId);

    void salvar(Aluno aluno);
}
