package com.locgym.agenda.web;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.locgym.agenda.aplicacao.ServicoHorarios;
import com.locgym.agenda.aplicacao.ServicoHorarios.DadosHorario;
import com.locgym.agenda.dominio.Horario;
import com.locgym.agenda.dominio.StatusHorario;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/horarios")
class HorariosController {

    record HorarioRequisicao(@NotNull UUID professorId, @NotNull Integer diaSemana, @NotNull LocalTime inicio,
                             @NotNull LocalTime fim, @NotBlank String tipo, StatusHorario status) {
        DadosHorario paraDados() {
            return new DadosHorario(professorId, diaSemana, inicio, fim, tipo);
        }
    }

    record HorarioResposta(UUID id, UUID professorId, String professorNome, int diaSemana,
                           @JsonFormat(pattern = "HH:mm") LocalTime inicio, @JsonFormat(pattern = "HH:mm") LocalTime fim,
                           String tipo, StatusHorario status) {
        static HorarioResposta de(Horario h, Map<UUID, String> nomes) {
            return new HorarioResposta(h.id(), h.professorId(), nomes.get(h.professorId()), h.diaSemana(),
                    h.inicio(), h.fim(), h.tipo(), h.status());
        }
    }

    private final ServicoHorarios servico;

    HorariosController(ServicoHorarios servico) {
        this.servico = servico;
    }

    @GetMapping
    List<HorarioResposta> listar(@RequestParam(required = false) Integer diaSemana,
                                 @RequestParam(required = false) StatusHorario status) {
        List<Horario> lista = servico.listar(diaSemana, status);
        var nomes = servico.nomesDosProfessores(lista.stream().map(Horario::professorId).distinct().toList());
        return lista.stream().map(h -> HorarioResposta.de(h, nomes)).toList();
    }

    @GetMapping("/{id}")
    HorarioResposta buscar(@PathVariable UUID id) {
        return resposta(servico.buscar(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    HorarioResposta criar(@Valid @RequestBody HorarioRequisicao r) {
        return resposta(servico.criar(r.paraDados()));
    }

    /** {@code status} nulo mantém o atual. */
    @PutMapping("/{id}")
    HorarioResposta alterar(@PathVariable UUID id, @Valid @RequestBody HorarioRequisicao r) {
        StatusHorario status = r.status() != null ? r.status() : servico.buscar(id).status();
        return resposta(servico.alterar(id, r.paraDados(), status));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void excluir(@PathVariable UUID id) {
        servico.excluir(id);
    }

    private HorarioResposta resposta(Horario h) {
        return HorarioResposta.de(h, servico.nomesDosProfessores(List.of(h.professorId())));
    }
}
