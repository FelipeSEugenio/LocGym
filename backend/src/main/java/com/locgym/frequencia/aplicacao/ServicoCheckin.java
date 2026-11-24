package com.locgym.frequencia.aplicacao;

import com.locgym.academia.Academias;
import com.locgym.agenda.Agenda;
import com.locgym.agenda.AulaAberta;
import com.locgym.agenda.AulaRegistrada;
import com.locgym.aluno.Alunos;
import com.locgym.aluno.Alunos.SituacaoAluno;
import com.locgym.compartilhado.auditoria.AcaoAuditada;
import com.locgym.compartilhado.auditoria.Auditoria;
import com.locgym.compartilhado.erros.NaoEncontradoException;
import com.locgym.compartilhado.erros.RegraVioladaException;
import com.locgym.compartilhado.eventos.PublicadorEventos;
import com.locgym.compartilhado.tempo.Calendario;
import com.locgym.compartilhado.tenancy.TenantContext;
import com.locgym.frequencia.aplicacao.EventosFrequencia.CheckinRegistrado;
import com.locgym.frequencia.dominio.Checkin;
import com.locgym.frequencia.dominio.CheckinRepositorio;
import com.locgym.frequencia.dominio.Origem;
import com.locgym.frequencia.dominio.Pontualidade;
import com.locgym.matricula.Matriculas;
import com.locgym.pagamento.Cobrancas;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicoCheckin {

    public record Alerta(LocalDate vencimentoMaisAntigo, long diasDeAtraso) {}

    /** {@code aula} nula = check-in livre. */
    public record ResultadoCheckin(UUID checkinId, UUID alunoId, String alunoNome, AulaRegistrada aula,
                                   Pontualidade pontualidade, Alerta alerta) {}

    private final ServicoTokenQr tokens;
    private final Agenda agenda;
    private final Alunos alunos;
    private final Matriculas matriculas;
    private final Cobrancas cobrancas;
    private final Academias academias;
    private final CheckinRepositorio checkins;
    private final PublicadorEventos eventos;
    private final Auditoria auditoria;
    private final Calendario calendario;
    private final Clock clock;

    public ServicoCheckin(ServicoTokenQr tokens, Agenda agenda, Alunos alunos, Matriculas matriculas,
                          Cobrancas cobrancas, Academias academias, CheckinRepositorio checkins,
                          PublicadorEventos eventos, Auditoria auditoria, Calendario calendario, Clock clock) {
        this.tokens = tokens;
        this.agenda = agenda;
        this.alunos = alunos;
        this.matriculas = matriculas;
        this.cobrancas = cobrancas;
        this.academias = academias;
        this.checkins = checkins;
        this.eventos = eventos;
        this.auditoria = auditoria;
        this.calendario = calendario;
        this.clock = clock;
    }

    @Transactional
    public ResultadoCheckin porQr(String token, UUID horarioId) {
        UUID alunoId = tokens.validar(token).alunoId();
        LocalDateTime agora = calendario.agora().truncatedTo(ChronoUnit.MINUTES);
        return registrar(alunoId, agora.toLocalDate(), agora.toLocalTime(), horarioId, Origem.QR);
    }

    @Transactional
    public ResultadoCheckin manual(UUID alunoId, LocalDate data, LocalTime hora, UUID horarioId) {
        Checkin.validarMomento(data, hora, calendario.agora());
        ResultadoCheckin r = registrar(alunoId, data, hora.truncatedTo(ChronoUnit.MINUTES), horarioId, Origem.MANUAL);
        auditoria.registrar(AcaoAuditada.PRESENCA_MANUAL_REGISTRADA, "checkin", r.checkinId(), null,
                Map.of("alunoId", alunoId, "data", data.toString(), "hora", hora.truncatedTo(ChronoUnit.MINUTES).toString(),
                        "aulaId", r.aula() == null ? "" : r.aula().id().toString()));
        return r;
    }

    /** Aulas abertas no momento informado (ou agora, em São Paulo). */
    @Transactional(readOnly = true)
    public List<AulaAberta> aulasAbertas(LocalDate data, LocalTime hora) {
        LocalDateTime agora = calendario.agora();
        return agenda.abertas(data != null ? data : agora.toLocalDate(), hora != null ? hora : agora.toLocalTime());
    }

    private ResultadoCheckin registrar(UUID alunoId, LocalDate data, LocalTime hora, UUID horarioId, Origem origem) {
        SituacaoAluno aluno = alunos.situacao(alunoId)
                .orElseThrow(() -> new NaoEncontradoException("Aluno não encontrado"));
        exigirElegivel(aluno, data);
        AulaRegistrada aula = EscolhaDeAula.escolher(agenda.abertas(data, hora), horarioId)
                .map(a -> agenda.garantirAula(a.horarioId(), data)).orElse(null);
        var contexto = TenantContext.exigir();
        int tolerancia = academias.toleranciaPontualidade(contexto.academiaId());
        Checkin checkin = Checkin.registrar(alunoId, aula == null ? null : aula.id(), aula == null ? null : aula.inicio(),
                data, hora, origem, tolerancia, contexto.usuarioId(), clock.instant());
        checkins.inserir(checkin);
        eventos.publicar(new CheckinRegistrado(contexto.academiaId(), checkin.id(), alunoId,
                aula == null ? null : aula.id(), aula == null ? null : aula.horarioId(), data, checkin.hora().toString(),
                origem.name(), checkin.pontualidade() == null ? null : checkin.pontualidade().name()));
        return new ResultadoCheckin(checkin.id(), alunoId, aluno.nome(), aula, checkin.pontualidade(), alerta(alunoId));
    }

    private void exigirElegivel(SituacaoAluno aluno, LocalDate data) {
        if (!aluno.ativo()) {
            throw new RegraVioladaException("ALUNO_INATIVO", "Aluno inativo");
        }
        var m = matriculas.vigenteDoAluno(aluno.id())
                .orElseThrow(() -> new RegraVioladaException("SEM_MATRICULA", "Aluno sem matrícula"));
        if (!"ATIVA".equals(m.status())) {
            throw new RegraVioladaException("MATRICULA_NAO_ATIVA", "Matrícula " + m.status().toLowerCase(),
                    Map.of("status", m.status()));
        }
        if (m.dataInicio().isAfter(data)) {
            throw new RegraVioladaException("MATRICULA_NAO_INICIADA", "A matrícula ainda não começou",
                    Map.of("dataInicio", m.dataInicio().toString()));
        }
    }

    private Alerta alerta(UUID alunoId) {
        if (!cobrancas.alunoInadimplente(alunoId)) {
            return null;
        }
        return cobrancas.vencimentoAtrasadoMaisAntigoDoAluno(alunoId)
                .map(v -> new Alerta(v, ChronoUnit.DAYS.between(v, calendario.hoje())))
                .orElse(null);
    }
}
