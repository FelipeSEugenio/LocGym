package com.locgym.aluno.web;

import com.locgym.aluno.aplicacao.ServicoAlunos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Portal do aluno (somente leitura nesta fase). Exige o perfil ALUNO na academia ativa. */
@RestController
class MeusDadosController {

    private final ServicoAlunos servico;

    MeusDadosController(ServicoAlunos servico) {
        this.servico = servico;
    }

    @GetMapping("/api/v1/eu/aluno")
    AlunoResposta meusDados() {
        return AlunoResposta.de(servico.meusDados());
    }
}
