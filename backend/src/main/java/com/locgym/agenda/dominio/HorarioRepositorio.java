package com.locgym.agenda.dominio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HorarioRepositorio {

    Optional<Horario> porId(UUID id);

    /** Filtros opcionais; ordenada por dia da semana, início e tipo. */
    List<Horario> listar(Integer diaSemanaOuNulo, StatusHorario statusOuNulo);

    List<Horario> ativosDoDia(int diaSemana);

    void salvar(Horario horario);

    void excluir(UUID id);
}
