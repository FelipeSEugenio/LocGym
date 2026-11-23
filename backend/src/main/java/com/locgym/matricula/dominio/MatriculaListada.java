package com.locgym.matricula.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Linha da listagem de matrículas (modelo de leitura, já com os nomes de aluno e plano). */
public record MatriculaListada(UUID id, UUID alunoId, String alunoNome, UUID planoId, String planoNome,
                               BigDecimal valor, String periodicidade, LocalDate dataInicio, LocalDate vigenteAte,
                               StatusMatricula status) {}
