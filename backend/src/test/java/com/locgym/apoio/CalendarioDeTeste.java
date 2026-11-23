package com.locgym.apoio;

import com.locgym.compartilhado.tempo.Calendario;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Data de negócio controlável nos testes ({@code @Import(CalendarioDeTeste.Config.class)}). Só a data de negócio
 * muda — JWT, outbox e auditoria continuam no relógio real, então tokens emitidos seguem válidos.
 */
public class CalendarioDeTeste extends Calendario {

    private volatile LocalDate hoje = LocalDate.now(FUSO);

    public CalendarioDeTeste() {
        super(Clock.systemUTC());
    }

    public void definir(LocalDate dia) {
        hoje = dia;
    }

    public void avancarDias(int dias) {
        hoje = hoje.plusDays(dias);
    }

    @Override
    public LocalDate hoje() {
        return hoje;
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
