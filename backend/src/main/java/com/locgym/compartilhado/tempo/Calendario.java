package com.locgym.compartilhado.tempo;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/**
 * Data de negócio. Regras de cobrança e vencimento usam sempre o dia em São Paulo — nunca a data UTC
 * nem {@code LocalDate.now()} — para que nada vença "um dia antes" entre 21h e meia-noite.
 */
@Component
public class Calendario {

    public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private final Clock clock;

    public Calendario(Clock clock) {
        this.clock = clock;
    }

    public LocalDate hoje() {
        return LocalDate.ofInstant(clock.instant(), FUSO);
    }

    /** Data e hora de negócio em São Paulo (check-in, pontualidade). */
    public java.time.LocalDateTime agora() {
        return java.time.LocalDateTime.ofInstant(clock.instant(), FUSO);
    }
}
