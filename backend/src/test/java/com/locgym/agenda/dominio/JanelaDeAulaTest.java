package com.locgym.agenda.dominio;

import static java.time.LocalTime.of;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JanelaDeAulaTest {

    @Test
    void abreTrintaMinutosAntesEFechaNoFim() {
        assertThat(JanelaDeAula.contem(of(19, 0), of(20, 0), of(18, 29))).isFalse();
        assertThat(JanelaDeAula.contem(of(19, 0), of(20, 0), of(18, 30))).isTrue();
        assertThat(JanelaDeAula.contem(of(19, 0), of(20, 0), of(19, 59))).isTrue();
        assertThat(JanelaDeAula.contem(of(19, 0), of(20, 0), of(20, 0))).isFalse();
    }

    @Test
    void recortaNaMeiaNoite() {
        assertThat(JanelaDeAula.contem(of(0, 15), of(1, 0), of(0, 0))).isTrue();
        assertThat(JanelaDeAula.contem(of(0, 15), of(1, 0), of(23, 50))).isFalse();
    }
}
