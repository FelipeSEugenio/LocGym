package com.locgym.matricula.aplicacao;

import com.locgym.matricula.dominio.Matricula;
import com.locgym.matricula.dominio.Periodo;
import com.locgym.pagamento.Cobrancas;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * Gera as cobranças que já deveriam existir — normalmente uma, alguns dias antes do vencimento; depois de meses
 * parados (ou de uma academia reativada), todas as que faltam, em ordem. Usado pela quitação e pela rotina diária.
 */
@Component
class RecuperacaoDePeriodos {

    private final Cobrancas cobrancas;

    RecuperacaoDePeriodos(Cobrancas cobrancas) {
        this.cobrancas = cobrancas;
    }

    /** @return quantas cobranças foram consideradas (a geração é idempotente) */
    int gerarFaltantes(Matricula m, int diasAntecedencia, LocalDate hoje) {
        LocalDate inicio = cobrancas.inicioDoProximoPeriodoSemCobranca(m.id(), m.vigenteAte());
        int total = 0;
        for (Periodo p : m.periodosAGerar(inicio, diasAntecedencia, hoje)) {
            cobrancas.gerar(m.id(), m.alunoId(), m.valor(), p.inicio(), p.fim());
            total++;
        }
        return total;
    }
}
