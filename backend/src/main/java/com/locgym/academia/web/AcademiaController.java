package com.locgym.academia.web;

import com.locgym.academia.aplicacao.ServicoAcademia;
import com.locgym.academia.dominio.StatusAcademia;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AcademiaController {

    record StatusRequisicao(@NotNull StatusAcademia status) {}

    private final ServicoAcademia servico;

    AcademiaController(ServicoAcademia servico) {
        this.servico = servico;
    }

    /** Qualquer usuário autenticado pode criar uma academia; vira administrador dela. */
    @PostMapping("/api/v1/academias")
    @ResponseStatus(HttpStatus.CREATED)
    AcademiaResposta criar(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DadosAcademiaRequisicao req) {
        return AcademiaResposta.de(servico.criar(req.paraDominio(), UUID.fromString(jwt.getSubject()),
                jwt.getClaimAsString("nome")));
    }

    @GetMapping("/api/v1/academia")
    AcademiaResposta ativa() {
        return AcademiaResposta.de(servico.ativa());
    }

    @PutMapping("/api/v1/academia")
    AcademiaResposta atualizar(@Valid @RequestBody DadosAcademiaRequisicao req) {
        return AcademiaResposta.de(servico.atualizar(req.paraDominio(), req.parametrosCobranca(), req.toleranciaPontualidade()));
    }

    @PatchMapping("/api/v1/academia/status")
    AcademiaResposta alterarStatus(@Valid @RequestBody StatusRequisicao req) {
        return AcademiaResposta.de(servico.alterarStatus(req.status()));
    }
}
