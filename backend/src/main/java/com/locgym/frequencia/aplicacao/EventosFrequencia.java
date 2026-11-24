package com.locgym.frequencia.aplicacao;

import com.locgym.compartilhado.eventos.EventoDominio;
import java.time.LocalDate;
import java.util.UUID;

/** Eventos publicados no Kafka (via outbox). Só ids, datas, horas ("HH:mm") e enums — nunca dados pessoais. */
final class EventosFrequencia {

    private EventosFrequencia() {}

    record CheckinRegistrado(UUID academiaId, UUID checkinId, UUID alunoId, UUID aulaId, UUID horarioId,
                             LocalDate data, String hora, String origem, String pontualidade) implements EventoDominio {
        public String tipo() { return "checkin.registrado"; }
        public int versao() { return 1; }
    }

    record CheckinCorrigido(UUID academiaId, UUID checkinAntigoId, UUID checkinNovoId, UUID alunoId, UUID aulaId,
                            UUID horarioId, LocalDate data, String hora, String origem, String pontualidade)
            implements EventoDominio {
        public String tipo() { return "checkin.corrigido"; }
        public int versao() { return 1; }
    }

    record CheckinExcluido(UUID academiaId, UUID checkinId, UUID alunoId, String motivo) implements EventoDominio {
        public String tipo() { return "checkin.excluido"; }
        public int versao() { return 1; }
    }
}
