package com.locgym.agenda;

import java.time.LocalTime;
import java.util.UUID;

/** Aula aceitando check-in num momento. {@code aulaId} é nulo enquanto a aula daquele dia ainda não foi criada. */
public record AulaAberta(UUID horarioId, UUID aulaId, String tipo, UUID professorId, String professorNome,
                         LocalTime inicio, LocalTime fim) {}
