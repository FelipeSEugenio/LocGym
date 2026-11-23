package com.locgym.matricula.aplicacao;

import com.locgym.academia.Academias;
import com.locgym.academia.ParametrosCobranca;
import com.locgym.compartilhado.eventos.PublicadorEventos;
import com.locgym.compartilhado.tempo.Calendario;
import com.locgym.compartilhado.tenancy.ContextoTenant;
import com.locgym.compartilhado.tenancy.TenantContext;
import com.locgym.matricula.aplicacao.EventosMatricula.MatriculaVencida;
import com.locgym.matricula.dominio.Matricula;
import com.locgym.matricula.dominio.MatriculaRepositorio;
import com.locgym.matricula.dominio.StatusMatricula;
import com.locgym.pagamento.Cobrancas;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Rotina diária multi-tenant. Percorre as academias ATIVAS e processa cada uma numa transação própria, no contexto
 * daquela academia ({@link ContextoTenant#sistema}) — o RLS continua valendo; nada é lido "de todas as academias".
 * Falha numa academia é registrada e não impede as demais. Idempotente.
 */
@Component
public class RotinaDiaria {

    private static final Logger log = LoggerFactory.getLogger(RotinaDiaria.class);

    public record ResultadoRotina(int academias, int falhas, int periodosVerificados, int cobrancasAtrasadas,
                                  int matriculasVencidas) {}

    private record ResultadoAcademia(int periodos, int atrasadas, int vencidas) {}

    private final Academias academias;
    private final MatriculaRepositorio matriculas;
    private final Cobrancas cobrancas;
    private final RecuperacaoDePeriodos recuperacao;
    private final PublicadorEventos eventos;
    private final Calendario calendario;
    private final TransactionTemplate tx;
    private final MeterRegistry metricas;

    RotinaDiaria(Academias academias, MatriculaRepositorio matriculas, Cobrancas cobrancas,
                 RecuperacaoDePeriodos recuperacao, PublicadorEventos eventos, Calendario calendario,
                 TransactionTemplate tx, MeterRegistry metricas) {
        this.academias = academias;
        this.matriculas = matriculas;
        this.cobrancas = cobrancas;
        this.recuperacao = recuperacao;
        this.eventos = eventos;
        this.calendario = calendario;
        this.tx = tx;
        this.metricas = metricas;
    }

    public ResultadoRotina executar() {
        LocalDate hoje = calendario.hoje();
        Optional<ContextoTenant> anterior = TenantContext.atual();
        int processadas = 0, falhas = 0, periodos = 0, atrasadas = 0, vencidas = 0;
        try {
            for (UUID academiaId : academias.ativas()) {
                processadas++;
                TenantContext.definir(ContextoTenant.sistema(academiaId));
                try {
                    ResultadoAcademia r = tx.execute(s -> processar(academiaId, hoje));
                    periodos += r.periodos();
                    atrasadas += r.atrasadas();
                    vencidas += r.vencidas();
                } catch (RuntimeException e) {
                    falhas++;
                    metricas.counter("locgym.rotina_diaria.falhas", "academia", academiaId.toString()).increment();
                    log.error("Rotina diária falhou para a academia {} em {}", academiaId, hoje, e);
                } finally {
                    TenantContext.limpar();
                }
            }
        } finally {
            anterior.ifPresent(TenantContext::definir);
        }
        log.info("Rotina diária de {}: {} academias, {} falhas, {} cobranças atrasadas, {} matrículas vencidas",
                hoje, processadas, falhas, atrasadas, vencidas);
        return new ResultadoRotina(processadas, falhas, periodos, atrasadas, vencidas);
    }

    private ResultadoAcademia processar(UUID academiaId, LocalDate hoje) {
        ParametrosCobranca parametros = academias.parametrosCobranca(academiaId);

        // 1. gerar cobranças (inclusive períodos parados) das matrículas ativas
        int periodos = 0;
        for (Matricula m : matriculas.porStatus(StatusMatricula.ATIVA)) {
            periodos += recuperacao.gerarFaltantes(m, parametros.diasAntecedencia(), hoje);
        }

        // 2. marcar atrasos (matrícula suspensa não atrasa)
        Set<UUID> suspensas = matriculas.porStatus(StatusMatricula.SUSPENSA).stream().map(Matricula::id)
                .collect(Collectors.toSet());
        int atrasadas = cobrancas.marcarAtrasadas(suspensas);

        // 3. vencer matrículas ativas com atraso além da carência
        int vencidas = 0;
        for (Matricula m : matriculas.porStatus(StatusMatricula.ATIVA)) {
            boolean alemDaCarencia = cobrancas.vencimentoAtrasadoMaisAntigo(m.id())
                    .map(v -> v.plusDays(parametros.diasCarencia()).isBefore(hoje)).orElse(false);
            if (m.vencerSe(alemDaCarencia)) {
                matriculas.salvar(m);
                eventos.publicar(new MatriculaVencida(academiaId, m.id(), m.alunoId(), m.vigenteAte()));
                vencidas++;
            }
        }
        return new ResultadoAcademia(periodos, atrasadas, vencidas);
    }
}
