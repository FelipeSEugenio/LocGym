package com.locgym.agenda.aplicacao;

import com.locgym.agenda.Agenda;
import com.locgym.agenda.AulaAberta;
import com.locgym.agenda.AulaRegistrada;
import com.locgym.agenda.dominio.AulaRepositorio;
import com.locgym.agenda.dominio.AulaRepositorio.Aula;
import com.locgym.agenda.dominio.HorarioRepositorio;
import com.locgym.agenda.dominio.JanelaDeAula;
import com.locgym.compartilhado.erros.NaoEncontradoException;
import com.locgym.professor.Professores;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AgendaImpl implements Agenda {

    private final HorarioRepositorio horarios;
    private final AulaRepositorio aulas;
    private final Professores professores;

    AgendaImpl(HorarioRepositorio horarios, AulaRepositorio aulas, Professores professores) {
        this.horarios = horarios;
        this.aulas = aulas;
        this.professores = professores;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AulaAberta> abertas(LocalDate data, LocalTime hora) {
        List<Aula> doDia = aulas.doDia(data);
        Set<UUID> horariosComAula = doDia.stream().map(Aula::horarioId).collect(Collectors.toSet());
        var resultado = new ArrayList<AulaAberta>();
        for (Aula a : doDia) {
            if (JanelaDeAula.contem(a.inicio(), a.fim(), hora)) {
                resultado.add(new AulaAberta(a.horarioId(), a.id(), a.tipo(), a.professorId(), null, a.inicio(), a.fim()));
            }
        }
        for (var h : horarios.ativosDoDia(data.getDayOfWeek().getValue())) {
            if (!horariosComAula.contains(h.id()) && JanelaDeAula.contem(h.inicio(), h.fim(), hora)) {
                resultado.add(new AulaAberta(h.id(), null, h.tipo(), h.professorId(), null, h.inicio(), h.fim()));
            }
        }
        Map<UUID, String> nomes = professores.nomes(resultado.stream().map(AulaAberta::professorId).toList());
        return resultado.stream()
                .map(a -> new AulaAberta(a.horarioId(), a.aulaId(), a.tipo(), a.professorId(),
                        nomes.get(a.professorId()), a.inicio(), a.fim()))
                .sorted(Comparator.comparing(AulaAberta::inicio).thenComparing(AulaAberta::tipo))
                .toList();
    }

    @Override
    @Transactional
    public AulaRegistrada garantirAula(UUID horarioId, LocalDate data) {
        var horario = horarios.porId(horarioId).orElseThrow(() -> new NaoEncontradoException("Horário não encontrado"));
        Aula a = aulas.garantir(horario, data);
        return registrada(a, professores.nomes(List.of(a.professorId())));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, AulaRegistrada> aulas(Collection<UUID> aulaIds) {
        Map<UUID, Aula> encontradas = aulas.porIds(aulaIds);
        Map<UUID, String> nomes = professores.nomes(encontradas.values().stream().map(Aula::professorId).toList());
        return encontradas.values().stream().collect(Collectors.toMap(Aula::id, a -> registrada(a, nomes)));
    }

    private static AulaRegistrada registrada(Aula a, Map<UUID, String> nomes) {
        return new AulaRegistrada(a.id(), a.horarioId(), a.data(), a.inicio(), a.fim(), a.professorId(),
                nomes.get(a.professorId()), a.tipo());
    }
}
