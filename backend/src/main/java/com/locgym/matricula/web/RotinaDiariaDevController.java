package com.locgym.matricula.web;

import com.locgym.matricula.aplicacao.RotinaDiaria;
import com.locgym.matricula.aplicacao.RotinaDiaria.ResultadoRotina;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Só no perfil {@code dev}: executa a rotina diária agora (na data real), para demonstrar o fluxo. */
@RestController
@Profile("dev")
class RotinaDiariaDevController {

    private final RotinaDiaria rotina;

    RotinaDiariaDevController(RotinaDiaria rotina) {
        this.rotina = rotina;
    }

    @PostMapping("/api/v1/dev/rotina-diaria")
    ResultadoRotina executar() {
        return rotina.executar();
    }
}
