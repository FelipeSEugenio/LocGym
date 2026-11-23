package com.locgym.matricula.aplicacao;

import com.locgym.compartilhado.paginacao.Pagina;
import com.locgym.compartilhado.tempo.Calendario;
import com.locgym.matricula.dominio.ConsultaInadimplencia;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicoInadimplencia {

    public record Inadimplente(UUID alunoId, String nome, String cpf, BigDecimal totalEmAtraso, int cobrancasAtrasadas,
                               LocalDate vencimentoMaisAntigo, int diasDeAtraso, UUID matriculaId,
                               String statusMatricula) {}

    private final ConsultaInadimplencia consulta;
    private final Calendario calendario;

    public ServicoInadimplencia(ConsultaInadimplencia consulta, Calendario calendario) {
        this.consulta = consulta;
        this.calendario = calendario;
    }

    @Transactional(readOnly = true)
    public Pagina<Inadimplente> listar(String busca, int pagina, int tamanho) {
        LocalDate hoje = calendario.hoje();
        return consulta.listar(busca, pagina, tamanho).mapear(l -> new Inadimplente(l.alunoId(), l.nome(), l.cpf(),
                l.totalEmAtraso(), l.cobrancasAtrasadas(), l.vencimentoMaisAntigo(),
                (int) ChronoUnit.DAYS.between(l.vencimentoMaisAntigo(), hoje), l.matriculaId(), l.statusMatricula()));
    }
}
