package com.locgym.matricula.dominio;

import static java.time.LocalDate.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CalendarioDePeriodosTest {

    static LocalDate proximo(LocalDate base, int meses, LocalDate inicio) {
        return CalendarioDePeriodos.periodoQueComecaEm(base, meses, inicio).fim();
    }

    @Test
    void fimDeMesSemDeriva() {
        LocalDate base = of(2026, 1, 31);
        LocalDate p1 = proximo(base, 1, base);
        LocalDate p2 = proximo(base, 1, p1);
        LocalDate p3 = proximo(base, 1, p2);

        assertThat(p1).isEqualTo(of(2026, 2, 28));
        assertThat(p2).isEqualTo(of(2026, 3, 31));
        assertThat(p3).isEqualTo(of(2026, 4, 30));
    }

    @Test
    void bissextoAnual() {
        LocalDate base = of(2028, 2, 29);
        LocalDate p1 = proximo(base, 12, base);
        assertThat(p1).isEqualTo(of(2029, 2, 28));
        assertThat(proximo(base, 12, p1)).isEqualTo(of(2030, 2, 28));
    }

    @Test
    void trimestral() {
        LocalDate base = of(2026, 11, 23);
        LocalDate p1 = proximo(base, 3, base);
        assertThat(p1).isEqualTo(of(2027, 2, 23));
        assertThat(proximo(base, 3, p1)).isEqualTo(of(2027, 5, 23));
    }

    @Test
    void periodoTemInicioEFim() {
        Periodo p = CalendarioDePeriodos.periodoQueComecaEm(of(2026, 11, 10), 1, of(2027, 1, 10));
        assertThat(p).isEqualTo(new Periodo(of(2027, 1, 10), of(2027, 2, 10)));
    }

    @Test
    void fimUsaDiaAncoraLimitadoAoMes() {
        assertThat(CalendarioDePeriodos.fimDoPeriodo(of(2026, 2, 28), 1, 31)).isEqualTo(of(2026, 3, 31));
        assertThat(CalendarioDePeriodos.fimDoPeriodo(of(2026, 3, 31), 1, 31)).isEqualTo(of(2026, 4, 30));
        assertThat(CalendarioDePeriodos.fimDoPeriodo(of(2026, 12, 11), 1, 10)).isEqualTo(of(2027, 1, 10));
    }

    @Test
    void primeiroLimiteDepoisDe() {
        // âncora 01/09, mensal: limites 01/10, 01/11, 01/12…
        assertThat(CalendarioDePeriodos.primeiroLimiteDepoisDe(of(2026, 9, 1), 1, of(2026, 11, 1))).isEqualTo(of(2026, 12, 1));
        assertThat(CalendarioDePeriodos.primeiroLimiteDepoisDe(of(2026, 9, 1), 1, of(2026, 11, 5))).isEqualTo(of(2026, 12, 1));
        assertThat(CalendarioDePeriodos.primeiroLimiteDepoisDe(of(2026, 9, 1), 1, of(2026, 8, 5))).isEqualTo(of(2026, 9, 1));
    }

    @Test
    void mesesPorPeriodicidade() {
        assertThat(CalendarioDePeriodos.meses("MENSAL")).isEqualTo(1);
        assertThat(CalendarioDePeriodos.meses("TRIMESTRAL")).isEqualTo(3);
        assertThat(CalendarioDePeriodos.meses("SEMESTRAL")).isEqualTo(6);
        assertThat(CalendarioDePeriodos.meses("ANUAL")).isEqualTo(12);
        assertThatThrownBy(() -> CalendarioDePeriodos.meses("QUINZENAL")).isInstanceOf(IllegalArgumentException.class);
    }
}
