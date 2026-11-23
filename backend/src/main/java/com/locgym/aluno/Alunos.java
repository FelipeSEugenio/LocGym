package com.locgym.aluno;

import java.util.Optional;
import java.util.UUID;

/** API pública do módulo de alunos para outros módulos. Opera na academia do contexto atual. */
public interface Alunos {

    record SituacaoAluno(UUID id, String nome, boolean ativo) {}

    Optional<SituacaoAluno> situacao(UUID alunoId);
}
