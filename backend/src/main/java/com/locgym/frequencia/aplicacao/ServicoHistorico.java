package com.locgym.frequencia.aplicacao;

import com.locgym.agenda.Agenda;
import com.locgym.agenda.AulaRegistrada;
import com.locgym.aluno.Alunos;
import com.locgym.compartilhado.paginacao.Pagina;
import com.locgym.compartilhado.tempo.Calendario;
import com.locgym.frequencia.dominio.Checkin;
import com.locgym.frequencia.dominio.CheckinRepositorio;
import com.locgym.frequencia.dominio.CheckinRepositorio.FiltroCheckins;
import com.locgym.frequencia.dominio.StatusCheckin;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicoHistorico {

    /** {@code aula} nula = check-in livre. */
    public record ItemHistorico(Checkin checkin, String alunoNome, AulaRegistrada aula) {}

    private final CheckinRepositorio checkins;
    private final Alunos alunos;
    private final Agenda agenda;
    private final Calendario calendario;

    public ServicoHistorico(CheckinRepositorio checkins, Alunos alunos, Agenda agenda, Calendario calendario) {
        this.checkins = checkins;
        this.alunos = alunos;
        this.agenda = agenda;
        this.calendario = calendario;
    }

    /** Padrões: últimos 30 dias, só registros ATIVOS. */
    @Transactional(readOnly = true)
    public Pagina<ItemHistorico> listar(UUID alunoId, UUID horarioId, LocalDate de, LocalDate ate,
                                        StatusCheckin status, int pagina, int tamanho) {
        LocalDate hoje = calendario.hoje();
        var filtro = new FiltroCheckins(alunoId, horarioId, de != null ? de : hoje.minusDays(Checkin.DIAS_RETROATIVOS),
                ate != null ? ate : hoje, status != null ? status : StatusCheckin.ATIVO);
        Pagina<Checkin> encontrados = checkins.listar(filtro, pagina, tamanho);
        Map<UUID, String> nomes = alunos.nomes(encontrados.itens().stream().map(Checkin::alunoId).distinct().toList());
        Map<UUID, AulaRegistrada> aulas = agenda.aulas(
                encontrados.itens().stream().map(Checkin::aulaId).filter(Objects::nonNull).distinct().toList());
        return encontrados.mapear(c -> new ItemHistorico(c, nomes.get(c.alunoId()),
                c.aulaId() == null ? null : aulas.get(c.aulaId())));
    }
}
