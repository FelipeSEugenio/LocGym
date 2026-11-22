package com.locgym.professor.web;

import com.locgym.compartilhado.paginacao.Pagina;
import com.locgym.professor.aplicacao.ServicoProfessores;
import com.locgym.professor.dominio.Professor;
import com.locgym.professor.dominio.StatusProfessor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/professores")
class ProfessoresController {

    record ProfessorRequisicao(@NotBlank @Size(max = 150) String nome, @Size(max = 254) String email,
                               @Size(max = 20) String telefone, @Past LocalDate dataNascimento,
                               @Size(max = 100) String especialidade) {
        Professor.Dados paraDados() {
            return new Professor.Dados(nome, email, telefone, dataNascimento, especialidade);
        }
    }

    record StatusRequisicao(@NotNull StatusProfessor status) {}

    record ProfessorResposta(UUID id, String nome, String email, String telefone, LocalDate dataNascimento,
                             String especialidade, StatusProfessor status) {
        static ProfessorResposta de(Professor p) {
            var d = p.dados();
            return new ProfessorResposta(p.id(), d.nome(), d.email(), d.telefone(), d.dataNascimento(),
                    d.especialidade(), p.status());
        }
    }

    private final ServicoProfessores servico;

    ProfessoresController(ServicoProfessores servico) {
        this.servico = servico;
    }

    @GetMapping
    Pagina<ProfessorResposta> buscar(@RequestParam(required = false) String busca,
                                     @RequestParam(required = false) StatusProfessor status,
                                     @RequestParam(required = false) Integer pagina,
                                     @RequestParam(required = false) Integer tamanho) {
        return servico.buscar(busca, status, Pagina.paginaValida(pagina), Pagina.tamanhoValido(tamanho))
                .mapear(ProfessorResposta::de);
    }

    @GetMapping("/{id}")
    ProfessorResposta buscar(@PathVariable UUID id) {
        return ProfessorResposta.de(servico.buscar(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ProfessorResposta criar(@Valid @RequestBody ProfessorRequisicao r) {
        return ProfessorResposta.de(servico.criar(r.paraDados()));
    }

    @PutMapping("/{id}")
    ProfessorResposta atualizar(@PathVariable UUID id, @Valid @RequestBody ProfessorRequisicao r) {
        return ProfessorResposta.de(servico.atualizar(id, r.paraDados()));
    }

    @PatchMapping("/{id}/status")
    ProfessorResposta alterarStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequisicao r) {
        return ProfessorResposta.de(servico.alterarStatus(id, r.status()));
    }
}
