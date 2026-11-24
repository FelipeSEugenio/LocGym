package com.locgym.compartilhado.tenancy;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Rota de tenant usada só nos testes (registrada pelo component scan dos testes): devolve o app.academia_id visto pelo banco dentro da transação. */
@RestController
class ControllerDeContextoDeTeste {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    ControllerDeContextoDeTeste(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @GetMapping("/api/v1/teste-contexto")
    java.util.Map<String, String> academiaNoBanco() {
        String valor = tx.execute(s -> jdbc.queryForObject("select current_setting('app.academia_id', true)", String.class));
        return java.util.Map.of("academiaId", valor == null ? "" : valor);
    }

    @PostMapping("/api/v1/teste-contexto")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void escrever() {}

    @GetMapping("/api/v1/teste-regra-com-extras")
    void regraComExtras() {
        throw new com.locgym.compartilhado.erros.RegraVioladaException(
                "X", "m", java.util.Map.of("aulas", java.util.List.of("a")));
    }
}
