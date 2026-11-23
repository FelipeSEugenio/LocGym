package com.locgym.matricula.web;

import com.locgym.compartilhado.paginacao.Pagina;
import com.locgym.matricula.aplicacao.ServicoInadimplencia;
import com.locgym.matricula.aplicacao.ServicoInadimplencia.Inadimplente;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class InadimplenciaController {

    private final ServicoInadimplencia servico;

    InadimplenciaController(ServicoInadimplencia servico) {
        this.servico = servico;
    }

    @GetMapping("/api/v1/inadimplencia")
    Pagina<Inadimplente> listar(@RequestParam(required = false) String busca,
                                @RequestParam(required = false) Integer pagina,
                                @RequestParam(required = false) Integer tamanho) {
        return servico.listar(busca, Pagina.paginaValida(pagina), Pagina.tamanhoValido(tamanho));
    }
}
