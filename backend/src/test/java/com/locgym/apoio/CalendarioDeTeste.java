package com.locgym.apoio;

import com.locgym.compartilhado.tempo.Calendario;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Data e hora de negócio controláveis nos testes ({@code @Import(CalendarioDeTeste.Config.class)}). Só o tempo de
 * negócio muda — JWT, outbox e auditoria continuam no relógio real, então tokens emitidos seguem válidos.
 */
public class CalendarioDeTeste extends Calendario {

    private volatile LocalDateTime agora = LocalDateTime.now(FUSO);

    public CalendarioDeTeste() {
        super(Clock.systemUTC());
    }

    /** Define o dia; a hora fica em 12:00. */
    public void definir(LocalDate dia) {
        agora = dia.atTime(LocalTime.NOON);
    }

    public void definir(LocalDateTime momento) {
        agora = momento;
    }

    public void avancarDias(int dias) {
        agora = agora.plusDays(dias);
    }

    public void avancarMinutos(int minutos) {
        agora = agora.plusMinutes(minutos);
    }

    @Override
    public LocalDate hoje() {
        return agora.toLocalDate();
    }

    @Override
    public LocalDateTime agora() {
        return agora;
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {
        @Bean
        @Primary
        CalendarioDeTeste calendarioDeTeste() {
            return new CalendarioDeTeste();
        }
    }
}
