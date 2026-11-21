package com.locgym.compartilhado.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = TratadorGlobalDeErrosTest.ControllerDeTeste.class)
@Import({TratadorGlobalDeErrosTest.ControllerDeTeste.class, TratadorGlobalDeErros.class})
class TratadorGlobalDeErrosTest {

    @Autowired MockMvcTester mvc;

    record Pedido(@NotBlank String nome) {}

    @RestController
    static class ControllerDeTeste {
        @PostMapping("/teste/validacao")
        String validar(@Valid @RequestBody Pedido pedido) {
            return pedido.nome();
        }

        @GetMapping("/teste/erro")
        String falhar() {
            throw new RuntimeException("segredo");
        }
    }

    @Test
    void validacaoRetornaProblemDetail400() {
        var r = mvc.post().uri("/teste/validacao").contentType(MediaType.APPLICATION_JSON).content("{}");
        assertThat(r).hasStatus(400)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(r).bodyJson().extractingPath("$.erros[0].campo").isEqualTo("nome");
        assertThat(r).bodyJson().extractingPath("$.erros[0].mensagem").asString().isNotBlank();
    }

    @Test
    void erroInesperadoNaoVazaDetalhe() {
        var r = mvc.get().uri("/teste/erro");
        assertThat(r).hasStatus(500)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(r).bodyText().doesNotContain("segredo").doesNotContain("stackTrace");
    }
}
