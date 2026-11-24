package com.locgym.agenda.aplicacao;

import com.locgym.agenda.dominio.AulaRepositorio;
import com.locgym.agenda.dominio.Horario;
import com.locgym.agenda.dominio.HorarioRepositorio;
import com.locgym.agenda.dominio.StatusHorario;
import com.locgym.compartilhado.auditoria.AcaoAuditada;
import com.locgym.compartilhado.auditoria.Auditoria;
import com.locgym.compartilhado.erros.NaoEncontradoException;
import com.locgym.compartilhado.erros.RegraVioladaException;
import com.locgym.professor.Professores;
import java.time.LocalTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicoHorarios {

    public record DadosHorario(UUID professorId, int diaSemana, LocalTime inicio, LocalTime fim, String tipo) {}

    private final HorarioRepositorio horarios;
    private final AulaRepositorio aulas;
    private final Professores professores;
    private final Auditoria auditoria;

    public ServicoHorarios(HorarioRepositorio horarios, AulaRepositorio aulas, Professores professores,
                           Auditoria auditoria) {
        this.horarios = horarios;
        this.aulas = aulas;
        this.professores = professores;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public List<Horario> listar(Integer diaSemana, StatusHorario status) {
        return horarios.listar(diaSemana, status);
    }

    @Transactional(readOnly = true)
    public Horario buscar(UUID id) {
        return horarios.porId(id).orElseThrow(() -> new NaoEncontradoException("Horário não encontrado"));
    }

    @Transactional(readOnly = true)
    public Map<UUID, String> nomesDosProfessores(Collection<UUID> ids) {
        return professores.nomes(ids);
    }

    @Transactional
    public Horario criar(DadosHorario d) {
        exigirProfessorAtivo(d.professorId());
        Horario h = Horario.novo(d.professorId(), d.diaSemana(), d.inicio(), d.fim(), d.tipo());
        horarios.salvar(h);
        auditoria.registrar(AcaoAuditada.HORARIO_CRIADO, "horario", h.id(), null, retrato(h));
        return h;
    }

    @Transactional
    public Horario alterar(UUID id, DadosHorario d, StatusHorario status) {
        Horario h = buscar(id);
        if (!h.professorId().equals(d.professorId())) {
            exigirProfessorAtivo(d.professorId());
        }
        var antes = retrato(h);
        h.alterar(d.professorId(), d.diaSemana(), d.inicio(), d.fim(), d.tipo(), status);
        horarios.salvar(h);
        auditoria.registrar(AcaoAuditada.HORARIO_ALTERADO, "horario", h.id(), antes, retrato(h));
        return h;
    }

    @Transactional
    public void excluir(UUID id) {
        Horario h = buscar(id);
        if (aulas.horarioTemAulas(id)) {
            throw new RegraVioladaException("HORARIO_COM_AULAS",
                    "Este horário já teve aulas e não pode ser excluído; inative-o");
        }
        horarios.excluir(id);
        auditoria.registrar(AcaoAuditada.HORARIO_EXCLUIDO, "horario", id, retrato(h), null);
    }

    private void exigirProfessorAtivo(UUID professorId) {
        var p = professores.situacao(professorId)
                .orElseThrow(() -> new NaoEncontradoException("Professor não encontrado"));
        if (!p.ativo()) {
            throw new RegraVioladaException("PROFESSOR_INATIVO", "O professor está inativo");
        }
    }

    private static Map<String, Object> retrato(Horario h) {
        var m = new LinkedHashMap<String, Object>();
        m.put("professorId", h.professorId());
        m.put("diaSemana", h.diaSemana());
        m.put("inicio", h.inicio().toString());
        m.put("fim", h.fim().toString());
        m.put("tipo", h.tipo());
        m.put("status", h.status().name());
        return m;
    }
}
