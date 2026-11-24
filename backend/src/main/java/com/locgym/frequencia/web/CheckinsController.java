package com.locgym.frequencia.web;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.locgym.agenda.AulaRegistrada;
import com.locgym.frequencia.aplicacao.ServicoCheckin;
import com.locgym.frequencia.aplicacao.ServicoCheckin.Alerta;
import com.locgym.frequencia.aplicacao.ServicoCheckin.ResultadoCheckin;
import com.locgym.compartilhado.paginacao.Pagina;
import com.locgym.frequencia.aplicacao.ServicoHistorico;
import com.locgym.frequencia.aplicacao.ServicoHistorico.ItemHistorico;
import com.locgym.frequencia.dominio.Origem;
import com.locgym.frequencia.dominio.Pontualidade;
import com.locgym.frequencia.dominio.StatusCheckin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
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

    record MotivoRequisicao(@NotBlank String motivo) {}

    record CorrecaoRequisicao(@NotNull LocalDate data, @NotNull LocalTime hora, UUID horarioId, @NotBlank String motivo) {}

    record ItemResposta(UUID id, UUID alunoId, String alunoNome, LocalDate data,
                        @JsonFormat(pattern = "HH:mm") LocalTime hora, Origem origem, Pontualidade pontualidade,
                        StatusCheckin status, String motivo, AulaResumo aula) {
        static ItemResposta de(ItemHistorico i) {
            var c = i.checkin();
            return new ItemResposta(c.id(), c.alunoId(), i.alunoNome(), c.data(), c.hora(), c.origem(),
                    c.pontualidade(), c.status(), c.motivo(), AulaResumo.de(i.aula()));
        }
    }

    private final ServicoCheckin servico;
    private final ServicoHistorico historico;

    CheckinsController(ServicoCheckin servico, ServicoHistorico historico) {
        this.servico = servico;
        this.historico = historico;
    }

    @GetMapping
    Pagina<ItemResposta> listar(@RequestParam(required = false) UUID alunoId,
                                @RequestParam(required = false) UUID horarioId,
                                @RequestParam(required = false) LocalDate de,
                                @RequestParam(required = false) LocalDate ate,
                                @RequestParam(required = false) StatusCheckin status,
                                @RequestParam(required = false) Integer pagina,
                                @RequestParam(required = false) Integer tamanho) {
        return historico.listar(alunoId, horarioId, de, ate, status, Pagina.paginaValida(pagina),
                Pagina.tamanhoValido(tamanho)).mapear(ItemResposta::de);
    }

    @PostMapping("/{id}/excluir")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void excluir(@PathVariable UUID id, @Valid @RequestBody MotivoRequisicao r) {
        servico.excluir(id, r.motivo());
    }

    @PostMapping("/{id}/corrigir")
    @ResponseStatus(HttpStatus.CREATED)
    CheckinResposta corrigir(@PathVariable UUID id, @Valid @RequestBody CorrecaoRequisicao r) {
        return CheckinResposta.de(servico.corrigir(id, r.data(), r.hora(), r.horarioId(), r.motivo()));
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
