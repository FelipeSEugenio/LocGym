package com.locgym.matricula.aplicacao;

import com.locgym.aluno.Alunos;
import com.locgym.aluno.Alunos.SituacaoAluno;
import com.locgym.compartilhado.auditoria.AcaoAuditada;
import com.locgym.compartilhado.auditoria.Auditoria;
import com.locgym.compartilhado.erros.NaoEncontradoException;
import com.locgym.compartilhado.erros.RegraVioladaException;
import com.locgym.compartilhado.erros.ValorInvalidoException;
import com.locgym.compartilhado.eventos.PublicadorEventos;
import com.locgym.compartilhado.paginacao.Pagina;
import com.locgym.compartilhado.tempo.Calendario;
import com.locgym.compartilhado.tenancy.TenantContext;
import com.locgym.matricula.aplicacao.EventosMatricula.MatriculaCancelada;
import com.locgym.matricula.aplicacao.EventosMatricula.MatriculaCriada;
import com.locgym.matricula.aplicacao.EventosMatricula.MatriculaReativada;
import com.locgym.matricula.aplicacao.EventosMatricula.MatriculaSuspensa;
import com.locgym.matricula.dominio.CalendarioDePeriodos;
import com.locgym.matricula.dominio.Matricula;
import com.locgym.matricula.dominio.MatriculaListada;
import com.locgym.matricula.dominio.MatriculaRepositorio;
import com.locgym.matricula.dominio.Periodo;
import com.locgym.matricula.dominio.StatusMatricula;
import com.locgym.pagamento.CobrancaResumo;
import com.locgym.pagamento.Cobrancas;
import com.locgym.plano.Planos;
import com.locgym.plano.Planos.PlanoParaMatricula;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicoMatriculas {

    /** Matrícula com tudo o que a tela de detalhe mostra. */
    public record Detalhe(Matricula matricula, SituacaoAluno aluno, PlanoParaMatricula plano, boolean inadimplente,
                          List<Periodo> periodos, List<CobrancaResumo> cobrancas) {}

    private final MatriculaRepositorio matriculas;
    private final Cobrancas cobrancas;
    private final Alunos alunos;
    private final Planos planos;
    private final Calendario calendario;
    private final Auditoria auditoria;
    private final PublicadorEventos eventos;

    public ServicoMatriculas(MatriculaRepositorio matriculas, Cobrancas cobrancas, Alunos alunos, Planos planos,
                             Calendario calendario, Auditoria auditoria, PublicadorEventos eventos) {
        this.matriculas = matriculas;
        this.cobrancas = cobrancas;
        this.alunos = alunos;
        this.planos = planos;
        this.calendario = calendario;
        this.auditoria = auditoria;
        this.eventos = eventos;
    }

    @Transactional(readOnly = true)
    public Pagina<MatriculaListada> listar(StatusMatricula statusOuNulo, String buscaOuNulo, int pagina, int tamanho) {
        return matriculas.listar(statusOuNulo, buscaOuNulo, pagina, tamanho);
    }

    @Transactional(readOnly = true)
    public Detalhe detalhe(UUID id) {
        return detalheDe(buscar(id));
    }

    @Transactional(readOnly = true)
    public Detalhe daAluno(UUID alunoId) {
        return detalheDe(matriculas.naoCanceladaDoAluno(alunoId)
                .orElseThrow(() -> new NaoEncontradoException("Aluno sem matrícula")));
    }

    @Transactional
    public Detalhe criar(UUID alunoId, UUID planoId, LocalDate dataInicio, BigDecimal valorOuNulo) {
        SituacaoAluno aluno = alunos.situacao(alunoId).orElseThrow(() -> new NaoEncontradoException("Aluno não encontrado"));
        if (!aluno.ativo()) {
            throw new RegraVioladaException("ALUNO_NAO_ATIVO", "Só aluno ativo pode ser matriculado");
        }
        PlanoParaMatricula plano = planos.paraMatricula(planoId)
                .orElseThrow(() -> new NaoEncontradoException("Plano não encontrado"));
        if (!plano.ativo()) {
            throw new RegraVioladaException("PLANO_NAO_ATIVO", "O plano está inativo");
        }
        if (matriculas.naoCanceladaDoAluno(alunoId).isPresent()) {
            throw jaExiste();
        }
        Matricula m = Matricula.nova(alunoId, planoId, valorOuNulo == null ? plano.valor() : valorOuNulo,
                plano.periodicidade(), dataInicio, calendario.hoje());
        matriculas.inserir(m);
        Periodo primeiro = m.primeiroPeriodo();
        cobrancas.gerar(m.id(), alunoId, m.valor(), primeiro.inicio(), primeiro.fim());
        eventos.publicar(new MatriculaCriada(academia(), m.id(), alunoId, planoId, m.valor(), m.dataInicio()));
        auditoria.registrar(AcaoAuditada.MATRICULA_CRIADA, "matricula", m.id(), null,
                Map.of("alunoId", alunoId.toString(), "planoId", planoId.toString(),
                        "valor", m.valor().toPlainString(), "dataInicio", m.dataInicio().toString()));
        return detalheDe(m);
    }

    /** Gera já a cobrança do próximo período, mesmo fora da janela de antecedência. */
    @Transactional
    public Detalhe renovar(UUID id) {
        Matricula m = buscar(id);
        if (m.status() != StatusMatricula.ATIVA) {
            throw new RegraVioladaException("MATRICULA_NAO_ATIVA", "Só matrícula ativa pode ser renovada");
        }
        if (cobrancas.temEmAberto(m.id())) {
            throw new RegraVioladaException("COBRANCA_EM_ABERTO", "Já existe cobrança em aberto para esta matrícula");
        }
        LocalDate inicio = cobrancas.inicioDoProximoPeriodoSemCobranca(m.id(), m.vigenteAte());
        Periodo p = CalendarioDePeriodos.periodoQueComecaEm(m.dataBase(), CalendarioDePeriodos.meses(m.periodicidade()), inicio);
        CobrancaResumo c = cobrancas.gerar(m.id(), m.alunoId(), m.valor(), p.inicio(), p.fim());
        auditoria.registrar(AcaoAuditada.MATRICULA_RENOVADA, "matricula", m.id(), null,
                Map.of("cobrancaId", c.id().toString(), "vencimento", c.vencimento().toString()));
        return detalheDe(m);
    }

    @Transactional
    public Detalhe suspender(UUID id, String motivo) {
        Matricula m = buscar(id);
        m.suspender(motivo, calendario.hoje());
        matriculas.salvar(m);
        eventos.publicar(new MatriculaSuspensa(academia(), m.id(), m.alunoId(), m.motivo()));
        auditoria.registrar(AcaoAuditada.MATRICULA_SUSPENSA, "matricula", m.id(), Map.of("status", "ATIVA"),
                Map.of("status", "SUSPENSA", "motivo", m.motivo()));
        return detalheDe(m);
    }

    @Transactional
    public Detalhe reativar(UUID id) {
        Matricula m = buscar(id);
        var r = m.reativar(calendario.hoje());
        matriculas.salvar(m);
        matriculas.deslocarPeriodosDesde(m.id(), r.desde(), r.dias());
        cobrancas.deslocar(m.id(), r.desde(), r.dias());
        eventos.publicar(new MatriculaReativada(academia(), m.id(), m.alunoId(), r.dias(), m.vigenteAte()));
        auditoria.registrar(AcaoAuditada.MATRICULA_REATIVADA, "matricula", m.id(), Map.of("status", "SUSPENSA"),
                Map.of("status", "ATIVA", "diasSuspensos", r.dias(), "vigenteAte", m.vigenteAte().toString()));
        return detalheDe(m);
    }

    @Transactional
    public Detalhe cancelar(UUID id, String motivo) {
        Matricula m = buscar(id);
        StatusMatricula anterior = m.status();
        m.cancelar(motivo);
        matriculas.salvar(m);
        cobrancas.cancelarPendentesDaMatricula(m.id());
        eventos.publicar(new MatriculaCancelada(academia(), m.id(), m.alunoId(), m.motivo()));
        auditoria.registrar(AcaoAuditada.MATRICULA_CANCELADA, "matricula", m.id(), Map.of("status", anterior.name()),
                Map.of("status", "CANCELADA", "motivo", m.motivo()));
        return detalheDe(m);
    }

    /** Abona em lote, da cobrança em aberto mais antiga até a indicada (ex.: meses parados de quem voltou). */
    @Transactional
    public Detalhe abonar(UUID id, UUID ateCobrancaId, String motivo) {
        Matricula m = buscar(id);
        if (ateCobrancaId == null) {
            throw new ValorInvalidoException("ateCobrancaId", "Informe até qual cobrança abonar");
        }
        List<UUID> abonadas = cobrancas.abonarAte(m.id(), ateCobrancaId, motivo);
        auditoria.registrar(AcaoAuditada.COBRANCAS_ABONADAS, "matricula", m.id(), null,
                Map.of("cobrancas", abonadas.stream().map(UUID::toString).toList(), "motivo", motivo.strip()));
        return detalheDe(buscar(id));
    }

    private Matricula buscar(UUID id) {
        return matriculas.porId(id).orElseThrow(() -> new NaoEncontradoException("Matrícula não encontrada"));
    }

    private Detalhe detalheDe(Matricula m) {
        SituacaoAluno aluno = alunos.situacao(m.alunoId()).orElseThrow();
        PlanoParaMatricula plano = planos.paraMatricula(m.planoId()).orElseThrow();
        return new Detalhe(m, aluno, plano, cobrancas.alunoInadimplente(m.alunoId()), matriculas.periodos(m.id()),
                cobrancas.daMatricula(m.id()));
    }

    static RegraVioladaException jaExiste() {
        return new RegraVioladaException("MATRICULA_JA_EXISTE", "Este aluno já possui matrícula");
    }

    private static UUID academia() {
        return TenantContext.exigir().academiaId();
    }
}
