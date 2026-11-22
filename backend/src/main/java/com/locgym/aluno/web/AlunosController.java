package com.locgym.aluno.web;

import com.locgym.aluno.aplicacao.ServicoAlunos;
import com.locgym.aluno.dominio.Aluno;
import com.locgym.aluno.dominio.Cpf;
import com.locgym.aluno.dominio.StatusAluno;
import com.locgym.compartilhado.paginacao.Pagina;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Locale;
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
@RequestMapping("/api/v1/alunos")
class AlunosController {

    /** Dados editáveis (o e-mail de login não é editado aqui). */
    record AlunoRequisicao(
            @NotBlank @Size(max = 150) String nome,
            @NotBlank String cpf,
            @NotBlank @Size(max = 20) String telefone,
            @NotNull @Past LocalDate dataNascimento,
            @NotBlank @Size(max = 200) String endereco,
            @NotBlank @Size(max = 100) String cidade,
            @NotBlank @Pattern(regexp = "[A-Za-z]{2}", message = "UF inválida") String estado,
            @NotBlank @Pattern(regexp = "\\d{5}-?\\d{3}", message = "CEP inválido") String cep) {

        Aluno.Dados paraDados() {
            return new Aluno.Dados(nome, Cpf.de(cpf), telefone, dataNascimento, endereco, cidade,
                    estado.toUpperCase(Locale.ROOT), cep.replaceAll("\\D", ""));
        }
    }

    record StatusRequisicao(@NotNull StatusAluno status) {}

    private final ServicoAlunos servico;

    AlunosController(ServicoAlunos servico) {
        this.servico = servico;
    }

    @GetMapping
    Pagina<AlunoResposta> buscar(@RequestParam(required = false) String busca,
                                 @RequestParam(required = false) StatusAluno status,
                                 @RequestParam(required = false) Integer pagina,
                                 @RequestParam(required = false) Integer tamanho) {
        return servico.buscar(busca, status, Pagina.paginaValida(pagina), Pagina.tamanhoValido(tamanho))
                .mapear(AlunoResposta::de);
    }

    @GetMapping("/{id}")
    AlunoResposta buscar(@PathVariable UUID id) {
        return AlunoResposta.de(servico.buscar(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AlunoResposta criar(@Valid @RequestBody CadastroAlunoRequisicao r) {
        return AlunoResposta.de(servico.criar(r.paraDados(), r.email(), r.senhaInicial()));
    }

    @PutMapping("/{id}")
    AlunoResposta atualizar(@PathVariable UUID id, @Valid @RequestBody AlunoRequisicao r) {
        return AlunoResposta.de(servico.atualizar(id, r.paraDados()));
    }

    @PatchMapping("/{id}/status")
    AlunoResposta alterarStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequisicao r) {
        return AlunoResposta.de(servico.alterarStatus(id, r.status()));
    }

    /** Cadastro: dados editáveis + e-mail de login + senha inicial. */
    record CadastroAlunoRequisicao(
            @NotBlank @Size(max = 150) String nome,
            @NotBlank @Size(max = 254) String email,
            @NotBlank String cpf,
            @NotBlank @Size(max = 20) String telefone,
            @NotNull @Past LocalDate dataNascimento,
            @NotBlank @Size(max = 200) String endereco,
            @NotBlank @Size(max = 100) String cidade,
            @NotBlank @Pattern(regexp = "[A-Za-z]{2}", message = "UF inválida") String estado,
            @NotBlank @Pattern(regexp = "\\d{5}-?\\d{3}", message = "CEP inválido") String cep,
            @NotBlank @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres") String senhaInicial) {

        Aluno.Dados paraDados() {
            return new AlunoRequisicao(nome, cpf, telefone, dataNascimento, endereco, cidade, estado, cep).paraDados();
        }
    }
}
