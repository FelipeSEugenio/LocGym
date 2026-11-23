package com.locgym.pagamento.aplicacao;

import com.locgym.compartilhado.auditoria.AcaoAuditada;
import com.locgym.compartilhado.auditoria.Auditoria;
import com.locgym.compartilhado.erros.NaoEncontradoException;
import com.locgym.compartilhado.erros.RegraVioladaException;
import com.locgym.compartilhado.eventos.PublicadorEventos;
import com.locgym.compartilhado.paginacao.Pagina;
import com.locgym.compartilhado.tempo.Calendario;
import com.locgym.compartilhado.tenancy.TenantContext;
import com.locgym.pagamento.CobrancaQuitada;
import com.locgym.pagamento.CobrancaResumo;
import com.locgym.pagamento.Cobrancas;
import com.locgym.pagamento.aplicacao.EventosPagamento.PagamentoAtrasado;
import com.locgym.pagamento.aplicacao.EventosPagamento.PagamentoCancelado;
import com.locgym.pagamento.aplicacao.EventosPagamento.PagamentoGerado;
import com.locgym.pagamento.aplicacao.EventosPagamento.PagamentoRegistrado;
import com.locgym.pagamento.dominio.Cobranca;
import com.locgym.pagamento.dominio.CobrancaRepositorio;
import com.locgym.pagamento.dominio.CobrancaRepositorio.FiltroCobrancas;
import com.locgym.pagamento.dominio.FormaPagamento;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicoCobrancas implements Cobrancas {

    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CobrancaRepositorio cobrancas;
    private final Calendario calendario;
    private final Auditoria auditoria;
    private final PublicadorEventos eventos;
    private final ApplicationEventPublisher quitacoes;

    public ServicoCobrancas(CobrancaRepositorio cobrancas, Calendario calendario, Auditoria auditoria,
                            PublicadorEventos eventos, ApplicationEventPublisher quitacoes) {
        this.cobrancas = cobrancas;
        this.calendario = calendario;
        this.auditoria = auditoria;
        this.eventos = eventos;
        this.quitacoes = quitacoes;
    }

    // ---- casos de uso da API REST ----

    @Transactional(readOnly = true)
    public Pagina<Cobranca> listar(FiltroCobrancas filtro, int pagina, int tamanho) {
        return cobrancas.listar(filtro, pagina, tamanho);
    }

    @Transactional
    public Cobranca registrarPagamento(UUID cobrancaId, LocalDate data, FormaPagamento forma, String observacoes) {
        Cobranca c = quitavel(cobrancaId);
        c.registrarPagamento(data, forma, observacoes, calendario.hoje());
        cobrancas.salvar(c);
        eventos.publicar(new PagamentoRegistrado(academia(), c.id(), c.matriculaId(), c.alunoId(), c.valor(),
                c.dataPagamento(), c.forma().name()));
        auditoria.registrar(AcaoAuditada.PAGAMENTO_REGISTRADO, "pagamento", c.id(), null,
                Map.of("dataPagamento", c.dataPagamento().toString(), "forma", c.forma().name(),
                        "valor", c.valor().toPlainString()));
        quitacoes.publishEvent(quitada(c, false));
        return c;
    }

    @Transactional
    public Cobranca abonar(UUID cobrancaId, String motivo) {
        Cobranca c = quitavel(cobrancaId);
        abonarSemAuditar(c, motivo);
        auditoria.registrar(AcaoAuditada.COBRANCA_ABONADA, "pagamento", c.id(), null,
                Map.of("motivo", c.motivoCancelamento()));
        return c;
    }

    // ---- API pública para o módulo de matrícula ----

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public CobrancaResumo gerar(UUID matriculaId, UUID alunoId, BigDecimal valor, LocalDate periodoInicio,
                                LocalDate periodoFim) {
        Cobranca nova = Cobranca.nova(matriculaId, alunoId, valor, periodoInicio, periodoFim, calendario.hoje());
        if (!cobrancas.inserirSeAusente(nova)) {
            return cobrancas.daMatricula(matriculaId).stream()
                    .filter(c -> c.periodoInicio().equals(periodoInicio)).findFirst().map(ServicoCobrancas::resumo)
                    .orElseThrow(() -> new IllegalStateException("Cobrança do período sumiu: " + periodoInicio));
        }
        eventos.publicar(new PagamentoGerado(academia(), nova.id(), matriculaId, alunoId, nova.valor(), nova.vencimento()));
        if (nova.status() == com.locgym.pagamento.dominio.StatusCobranca.ATRASADO) {
            publicarAtraso(nova);
        }
        return resumo(nova);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<CobrancaResumo> daMatricula(UUID matriculaId) {
        return cobrancas.daMatricula(matriculaId).stream().map(ServicoCobrancas::resumo).toList();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean temAtrasada(UUID matriculaId) {
        return cobrancas.temAtrasada(matriculaId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean temEmAberto(UUID matriculaId) {
        return !cobrancas.emAbertoDaMatricula(matriculaId).isEmpty();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean alunoInadimplente(UUID alunoId) {
        return cobrancas.alunoTemAtrasada(alunoId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<LocalDate> vencimentoAtrasadoMaisAntigo(UUID matriculaId) {
        return cobrancas.vencimentoAtrasadoMaisAntigo(matriculaId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public LocalDate inicioDoProximoPeriodoSemCobranca(UUID matriculaId, LocalDate aPartirDe) {
        return cobrancas.maiorFimDePeriodo(matriculaId).filter(fim -> fim.isAfter(aPartirDe)).orElse(aPartirDe);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public int marcarAtrasadas(Set<UUID> matriculasSuspensas) {
        LocalDate hoje = calendario.hoje();
        int total = 0;
        for (Cobranca c : cobrancas.pendentesVencidas(hoje, matriculasSuspensas)) {
            if (c.marcarAtrasadaSeVencida(hoje)) {
                cobrancas.salvar(c);
                publicarAtraso(c);
                total++;
            }
        }
        return total;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelarPendentesDaMatricula(UUID matriculaId) {
        for (Cobranca c : cobrancas.emAbertoDaMatricula(matriculaId)) {
            if (c.cancelarPorMatricula()) {
                cobrancas.salvar(c);
                eventos.publicar(new PagamentoCancelado(academia(), c.id(), c.matriculaId(), c.alunoId(), c.valor(),
                        c.motivoCancelamento()));
            }
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void deslocar(UUID matriculaId, LocalDate desde, int dias) {
        if (dias == 0) {
            return;
        }
        for (Cobranca c : cobrancas.daMatricula(matriculaId)) {
            c.deslocar(desde, dias);
            cobrancas.salvar(c);
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<UUID> abonarAte(UUID matriculaId, UUID ateCobrancaId, String motivo) {
        List<Cobranca> abertas = cobrancas.emAbertoDaMatricula(matriculaId);
        if (abertas.stream().noneMatch(c -> c.id().equals(ateCobrancaId))) {
            boolean daMatricula = cobrancas.porId(ateCobrancaId).filter(c -> c.matriculaId().equals(matriculaId)).isPresent();
            if (!daMatricula) {
                throw new NaoEncontradoException("Cobrança não encontrada");
            }
            throw new RegraVioladaException("COBRANCA_NAO_EM_ABERTO", "Esta cobrança já foi paga ou cancelada");
        }
        List<UUID> abonadas = new ArrayList<>();
        for (Cobranca c : abertas) {
            abonarSemAuditar(c, motivo);
            abonadas.add(c.id());
            if (c.id().equals(ateCobrancaId)) {
                break;
            }
        }
        return abonadas;
    }

    // ---- apoio ----

    /** Existe na academia e é a mais antiga em aberto da matrícula (quitação sempre em ordem). */
    private Cobranca quitavel(UUID cobrancaId) {
        Cobranca c = cobrancas.porId(cobrancaId).orElseThrow(() -> new NaoEncontradoException("Cobrança não encontrada"));
        if (!c.status().emAberto()) {
            throw new RegraVioladaException("COBRANCA_NAO_EM_ABERTO", "Esta cobrança já foi paga ou cancelada");
        }
        Cobranca maisAntiga = cobrancas.maisAntigaEmAberto(c.matriculaId()).orElseThrow();
        if (!maisAntiga.id().equals(c.id())) {
            throw new RegraVioladaException("QUITAR_MAIS_ANTIGA_PRIMEIRO",
                    "Quite primeiro a cobrança de " + DATA_BR.format(maisAntiga.vencimento()));
        }
        return c;
    }

    private void abonarSemAuditar(Cobranca c, String motivo) {
        c.abonar(motivo);
        cobrancas.salvar(c);
        eventos.publicar(new PagamentoCancelado(academia(), c.id(), c.matriculaId(), c.alunoId(), c.valor(),
                c.motivoCancelamento()));
        quitacoes.publishEvent(quitada(c, true));
    }

    private void publicarAtraso(Cobranca c) {
        eventos.publicar(new PagamentoAtrasado(academia(), c.id(), c.matriculaId(), c.alunoId(), c.valor(), c.vencimento()));
    }

    private static CobrancaQuitada quitada(Cobranca c, boolean abono) {
        return new CobrancaQuitada(c.id(), c.matriculaId(), c.periodoInicio(), c.periodoFim(), abono);
    }

    private static UUID academia() {
        return TenantContext.exigir().academiaId();
    }

    public static CobrancaResumo resumo(Cobranca c) {
        return new CobrancaResumo(c.id(), c.matriculaId(), c.alunoId(), c.valor(), c.vencimento(), c.periodoInicio(),
                c.periodoFim(), c.status().name(), c.dataPagamento(), c.forma() == null ? null : c.forma().name(),
                c.observacoes(), c.motivoCancelamento());
    }
}
