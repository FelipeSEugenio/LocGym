package com.locgym.compartilhado.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.locgym.compartilhado.erros.NaoAutorizadoException;
import com.locgym.compartilhado.erros.NaoEncontradoException;
import com.locgym.compartilhado.erros.RegraVioladaException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = TratadorGlobalDeErrosTest.ControllerDeTeste.class)
@AutoConfigureMockMvc(addFilters = false)
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

        @GetMapping("/teste/regra")
        String regra() {
            throw new RegraVioladaException("CPF_JA_CADASTRADO", "CPF já cadastrado nesta academia");
        }

        @GetMapping("/teste/nao-encontrado")
        String naoEncontrado() {
            throw new NaoEncontradoException("Aluno não encontrado");
        }

        @GetMapping("/teste/nao-autorizado")
        String naoAutorizado() {
            throw new NaoAutorizadoException("E-mail ou senha inválidos");
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

    @Test
    void regraVioladaRetorna409ComCodigo() {
        var r = mvc.get().uri("/teste/regra");
        assertThat(r).hasStatus(409).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(r).bodyJson().extractingPath("$.codigo").isEqualTo("CPF_JA_CADASTRADO");
        assertThat(r).bodyJson().extractingPath("$.detail").isEqualTo("CPF já cadastrado nesta academia");
    }

    @Test
    void naoEncontradoRetorna404() {
        var r = mvc.get().uri("/teste/nao-encontrado");
        assertThat(r).hasStatus(404);
        assertThat(r).bodyJson().extractingPath("$.detail").isEqualTo("Aluno não encontrado");
    }

    @Test
    void naoAutorizadoRetorna401() {
        var r = mvc.get().uri("/teste/nao-autorizado");
        assertThat(r).hasStatus(401);
        assertThat(r).bodyJson().extractingPath("$.detail").isEqualTo("E-mail ou senha inválidos");
    }
}
