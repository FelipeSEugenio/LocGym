package com.locgym.matricula.aplicacao;

import com.locgym.compartilhado.eventos.EventoDominio;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Eventos publicados no Kafka (via outbox). Só ids, valores e datas — nunca dados pessoais. */
final class EventosMatricula {

    private EventosMatricula() {}

    record MatriculaCriada(UUID academiaId, UUID matriculaId, UUID alunoId, UUID planoId, BigDecimal valor,
                           LocalDate dataInicio) implements EventoDominio {
        public String tipo() { return "matricula.criada"; }
        public int versao() { return 1; }
    }

    record MatriculaRenovada(UUID academiaId, UUID matriculaId, UUID alunoId, LocalDate vigenteAte) implements EventoDominio {
        public String tipo() { return "matricula.renovada"; }
        public int versao() { return 1; }
    }

    record MatriculaVencida(UUID academiaId, UUID matriculaId, UUID alunoId, LocalDate vigenteAte) implements EventoDominio {
        public String tipo() { return "matricula.vencida"; }
        public int versao() { return 1; }
    }

    record MatriculaSuspensa(UUID academiaId, UUID matriculaId, UUID alunoId, String motivo) implements EventoDominio {
        public String tipo() { return "matricula.suspensa"; }
        public int versao() { return 1; }
    }

    record MatriculaReativada(UUID academiaId, UUID matriculaId, UUID alunoId, int dias, LocalDate vigenteAte)
            implements EventoDominio {
        public String tipo() { return "matricula.reativada"; }
        public int versao() { return 1; }
    }

    record MatriculaCancelada(UUID academiaId, UUID matriculaId, UUID alunoId, String motivo) implements EventoDominio {
        public String tipo() { return "matricula.cancelada"; }
        public int versao() { return 1; }
    }
}
