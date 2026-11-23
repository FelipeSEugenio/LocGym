package com.locgym.compartilhado.tempo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CalendarioTest {

    @Test
    void hojeUsaFusoDeSaoPaulo() {
        var noite = new Calendario(Clock.fixed(Instant.parse("2026-11-24T01:30:00Z"), ZoneOffset.UTC));
        var madrugada = new Calendario(Clock.fixed(Instant.parse("2026-11-24T03:00:00Z"), ZoneOffset.UTC));

        assertThat(noite.hoje()).isEqualTo(LocalDate.of(2026, 11, 23));
        assertThat(madrugada.hoje()).isEqualTo(LocalDate.of(2026, 11, 24));
    }
}
