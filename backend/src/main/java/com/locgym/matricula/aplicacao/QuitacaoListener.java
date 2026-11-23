package com.locgym.matricula.aplicacao;

import com.locgym.academia.Academias;
import com.locgym.compartilhado.eventos.PublicadorEventos;
import com.locgym.compartilhado.tempo.Calendario;
import com.locgym.compartilhado.tenancy.TenantContext;
import com.locgym.matricula.aplicacao.EventosMatricula.MatriculaRenovada;
import com.locgym.matricula.dominio.Matricula;
import com.locgym.matricula.dominio.MatriculaRepositorio;
import com.locgym.matricula.dominio.Periodo;
import com.locgym.matricula.dominio.StatusMatricula;
import com.locgym.pagamento.CobrancaQuitada;
import com.locgym.pagamento.Cobrancas;
import java.time.LocalDate;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pagamento/abono → vigência, na MESMA transação (ouvinte síncrono): o aluno que pagou nunca aparece vencido.
 * Uma exceção aqui desfaz o pagamento.
 */
@Component
class QuitacaoListener {

    private final MatriculaRepositorio matriculas;
    private final Cobrancas cobrancas;
    private final RecuperacaoDePeriodos recuperacao;
    private final Academias academias;
    private final PublicadorEventos eventos;
    private final Calendario calendario;

    QuitacaoListener(MatriculaRepositorio matriculas, Cobrancas cobrancas, RecuperacaoDePeriodos recuperacao,
                     Academias academias, PublicadorEventos eventos, Calendario calendario) {
        this.matriculas = matriculas;
        this.cobrancas = cobrancas;
        this.recuperacao = recuperacao;
        this.academias = academias;
        this.eventos = eventos;
        this.calendario = calendario;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    void aoQuitar(CobrancaQuitada e) {
        Matricula m = matriculas.porId(e.matriculaId())
                .orElseThrow(() -> new IllegalStateException("Matrícula da cobrança não encontrada: " + e.matriculaId()));
        if (m.status() == StatusMatricula.CANCELADA) {
            return;   // dívida de matrícula cancelada: só quita, não mexe na matrícula
        }
        LocalDate hoje = calendario.hoje();
        Periodo periodo = new Periodo(e.periodoInicio(), e.periodoFim());
        var quitacao = m.aplicarQuitacao(periodo, cobrancas.temAtrasada(m.id()), hoje);
        matriculas.salvar(m);
        matriculas.registrarPeriodo(m.id(), periodo, e.cobrancaId());
        if (!quitacao.primeira()) {
            eventos.publicar(new MatriculaRenovada(TenantContext.exigir().academiaId(), m.id(), m.alunoId(), m.vigenteAte()));
        }
        if (m.status() != StatusMatricula.SUSPENSA) {
            int antecedencia = academias.parametrosCobranca(TenantContext.exigir().academiaId()).diasAntecedencia();
            recuperacao.gerarFaltantes(m, antecedencia, hoje);
        }
    }
}
