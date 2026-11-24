package com.locgym.agenda.infraestrutura;

import com.locgym.agenda.dominio.Horario;
import com.locgym.agenda.dominio.HorarioRepositorio;
import com.locgym.agenda.dominio.StatusHorario;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class HorarioRepositorioJpa implements HorarioRepositorio {

    private final HorarioJpaDados dados;

    HorarioRepositorioJpa(HorarioJpaDados dados) {
        this.dados = dados;
    }

    @Override
    public Optional<Horario> porId(UUID id) {
        return dados.findById(id).map(HorarioJpa::paraDominio);
    }

    @Override
    public List<Horario> listar(Integer diaSemanaOuNulo, StatusHorario statusOuNulo) {
        Short dia = diaSemanaOuNulo == null ? null : diaSemanaOuNulo.shortValue();
        return dados.listar(dia, statusOuNulo).stream().map(HorarioJpa::paraDominio).toList();
    }

    @Override
    public List<Horario> ativosDoDia(int diaSemana) {
        return listar(diaSemana, StatusHorario.ATIVO);
    }

    /** Flush imediato: conflito de versão vira erro aqui, dentro da transação. */
    @Override
    public void salvar(Horario horario) {
        HorarioJpa entidade = dados.findById(horario.id()).orElseGet(() -> new HorarioJpa(horario.id()));
        entidade.copiar(horario);
        dados.saveAndFlush(entidade);
    }

    @Override
    public void excluir(UUID id) {
        dados.deleteById(id);
        dados.flush();
    }
}
