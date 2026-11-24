package com.locgym.matricula;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** API pública do módulo de matrículas (no contexto de tenant atual). */
public interface Matriculas {

    /** {@code status} = nome de {@code StatusMatricula}. */
    record SituacaoMatricula(UUID id, String status, LocalDate dataInicio) {}

    /** A matrícula não cancelada do aluno, se houver. */
    Optional<SituacaoMatricula> vigenteDoAluno(UUID alunoId);
}
