package com.locgym.matricula.infraestrutura;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EscapeLikeTest {

    @Test
    void curingasDigitadosSaoLiterais() {
        assertThat(MatriculaRepositorioJpa.escaparLike("100%")).isEqualTo("100\\%");
        assertThat(MatriculaRepositorioJpa.escaparLike("faixa_preta")).isEqualTo("faixa\\_preta");
        assertThat(MatriculaRepositorioJpa.escaparLike("a\\b")).isEqualTo("a\\\\b");
    }
}
