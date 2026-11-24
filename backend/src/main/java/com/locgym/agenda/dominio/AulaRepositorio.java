package com.locgym.agenda.dominio;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Aulas: ocorrências de um horário numa data, com cópia dos dados do horário no momento da criação. */
public interface AulaRepositorio {

    record Aula(UUID id, UUID horarioId, LocalDate data, LocalTime inicio, LocalTime fim, UUID professorId,
                String tipo) {}

    /** Devolve a aula do horário na data, criando-a (idempotente, segura sob concorrência) se não existir. */
    Aula garantir(Horario horario, LocalDate data);

    List<Aula> doDia(LocalDate data);

    boolean horarioTemAulas(UUID horarioId);

    Map<UUID, Aula> porIds(Collection<UUID> ids);
}
