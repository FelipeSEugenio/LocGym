package com.locgym.frequencia.web;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.locgym.agenda.AulaAberta;
import com.locgym.frequencia.aplicacao.ServicoCheckin;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class RecepcaoController {

    record AulaAbertaResposta(UUID horarioId, String tipo, String professorNome,
                              @JsonFormat(pattern = "HH:mm") LocalTime inicio, @JsonFormat(pattern = "HH:mm") LocalTime fim) {
        static AulaAbertaResposta de(AulaAberta a) {
            return new AulaAbertaResposta(a.horarioId(), a.tipo(), a.professorNome(), a.inicio(), a.fim());
        }
    }

    private final ServicoCheckin servico;

    RecepcaoController(ServicoCheckin servico) {
        this.servico = servico;
    }

    /** Aulas abertas agora (São Paulo), ou no momento informado (presença manual). */
    @GetMapping("/api/v1/recepcao/aulas-abertas")
    List<AulaAbertaResposta> aulasAbertas(@RequestParam(required = false) LocalDate data,
                                          @RequestParam(required = false) LocalTime hora) {
        return servico.aulasAbertas(data, hora).stream().map(AulaAbertaResposta::de).toList();
    }
}
