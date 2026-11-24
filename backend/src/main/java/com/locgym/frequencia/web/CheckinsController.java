package com.locgym.frequencia.web;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.locgym.agenda.AulaRegistrada;
import com.locgym.frequencia.aplicacao.ServicoCheckin;
import com.locgym.frequencia.aplicacao.ServicoCheckin.Alerta;
import com.locgym.frequencia.aplicacao.ServicoCheckin.ResultadoCheckin;
import com.locgym.frequencia.dominio.Pontualidade;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/checkins")
class CheckinsController {

    record QrRequisicao(@NotBlank String token, UUID horarioId) {}

    record ManualRequisicao(@NotNull UUID alunoId, @NotNull LocalDate data, @NotNull LocalTime hora, UUID horarioId) {}

    record AlunoResumo(UUID id, String nome) {}

    record AulaResumo(UUID horarioId, String tipo, String professorNome, @JsonFormat(pattern = "HH:mm") LocalTime inicio,
                      @JsonFormat(pattern = "HH:mm") LocalTime fim) {
        static AulaResumo de(AulaRegistrada a) {
            return a == null ? null : new AulaResumo(a.horarioId(), a.tipo(), a.professorNome(), a.inicio(), a.fim());
        }
    }

    record CheckinResposta(UUID checkinId, AlunoResumo aluno, AulaResumo aula, boolean livre, Pontualidade pontualidade,
                           Alerta alerta) {
        static CheckinResposta de(ResultadoCheckin r) {
            return new CheckinResposta(r.checkinId(), new AlunoResumo(r.alunoId(), r.alunoNome()), AulaResumo.de(r.aula()),
                    r.aula() == null, r.pontualidade(), r.alerta());
        }
    }

    private final ServicoCheckin servico;

    CheckinsController(ServicoCheckin servico) {
        this.servico = servico;
    }

    @PostMapping("/qr")
    @ResponseStatus(HttpStatus.CREATED)
    CheckinResposta porQr(@Valid @RequestBody QrRequisicao r) {
        return CheckinResposta.de(servico.porQr(r.token(), r.horarioId()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CheckinResposta manual(@Valid @RequestBody ManualRequisicao r) {
        return CheckinResposta.de(servico.manual(r.alunoId(), r.data(), r.hora(), r.horarioId()));
    }
}
