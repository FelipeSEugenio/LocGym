package com.locgym.agenda;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** API pública da agenda (no contexto de tenant atual). */
public interface Agenda {

    /**
     * Aulas abertas no momento: as aulas já criadas na data cuja janela (com os horários copiados) contém a hora,
     * mais os horários ATIVOS do dia da semana ainda sem aula na data cuja janela contém a hora. Ordenadas por início.
     */
    List<AulaAberta> abertas(LocalDate data, LocalTime hora);

    /**
     * A aula do horário na data, criada se ainda não existir.
     * @throws com.locgym.compartilhado.erros.NaoEncontradoException se o horário não existe na academia
     */
    AulaRegistrada garantirAula(UUID horarioId, LocalDate data);

    Map<UUID, AulaRegistrada> aulas(Collection<UUID> aulaIds);
}
