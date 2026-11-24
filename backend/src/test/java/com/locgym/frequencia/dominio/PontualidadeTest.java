package com.locgym.frequencia.dominio;

import static java.time.LocalTime.of;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PontualidadeTest {

    @Test
    void ateInicioMaisToleranciaInclusive() {
        assertThat(Pontualidade.de(of(19, 0), of(18, 40), 10)).isEqualTo(Pontualidade.PONTUAL);
        assertThat(Pontualidade.de(of(19, 0), of(19, 10), 10)).isEqualTo(Pontualidade.PONTUAL);
        assertThat(Pontualidade.de(of(19, 0), of(19, 11), 10)).isEqualTo(Pontualidade.ATRASADO);
    }

    @Test
    void toleranciaZero() {
        assertThat(Pontualidade.de(of(19, 0), of(19, 0), 0)).isEqualTo(Pontualidade.PONTUAL);
        assertThat(Pontualidade.de(of(19, 0), of(19, 1), 0)).isEqualTo(Pontualidade.ATRASADO);
    }
}
