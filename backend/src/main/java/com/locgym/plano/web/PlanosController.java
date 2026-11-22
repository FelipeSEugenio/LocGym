package com.locgym.plano.web;

import com.locgym.plano.aplicacao.ServicoPlanos;
import com.locgym.plano.dominio.Periodicidade;
import com.locgym.plano.dominio.Plano;
import com.locgym.plano.dominio.StatusPlano;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
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
@RequestMapping("/api/v1/planos")
class PlanosController {

    record PlanoRequisicao(@NotBlank @Size(max = 100) String nome, @Size(max = 500) String descricao,
                           @NotNull BigDecimal valor, @NotNull Periodicidade periodicidade) {}

    record StatusRequisicao(@NotNull StatusPlano status) {}

    record PlanoResposta(UUID id, String nome, String descricao, BigDecimal valor, Periodicidade periodicidade,
                         StatusPlano status) {
        static PlanoResposta de(Plano p) {
            return new PlanoResposta(p.id(), p.nome(), p.descricao(), p.valor(), p.periodicidade(), p.status());
        }
    }

    private final ServicoPlanos servico;

    PlanosController(ServicoPlanos servico) {
        this.servico = servico;
    }

    @GetMapping
    List<PlanoResposta> listar(@RequestParam(required = false) StatusPlano status) {
        return servico.listar(status).stream().map(PlanoResposta::de).toList();
    }

    @GetMapping("/{id}")
    PlanoResposta buscar(@PathVariable UUID id) {
        return PlanoResposta.de(servico.buscar(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PlanoResposta criar(@Valid @RequestBody PlanoRequisicao r) {
        return PlanoResposta.de(servico.criar(r.nome(), r.descricao(), r.valor(), r.periodicidade()));
    }

    @PutMapping("/{id}")
    PlanoResposta atualizar(@PathVariable UUID id, @Valid @RequestBody PlanoRequisicao r) {
        return PlanoResposta.de(servico.atualizar(id, r.nome(), r.descricao(), r.valor(), r.periodicidade()));
    }

    @PatchMapping("/{id}/status")
    PlanoResposta alterarStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequisicao r) {
        return PlanoResposta.de(servico.alterarStatus(id, r.status()));
    }
}
