package com.locgym.agenda;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Aula criada: cópia do horário no momento da criação. */
public record AulaRegistrada(UUID id, UUID horarioId, LocalDate data, LocalTime inicio, LocalTime fim,
                             UUID professorId, String professorNome, String tipo) {}
