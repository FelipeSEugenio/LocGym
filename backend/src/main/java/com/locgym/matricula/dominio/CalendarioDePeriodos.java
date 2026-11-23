package com.locgym.matricula.dominio;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Limites de período calculados sempre a partir da âncora ({@code dataBase.plusMonths(n × meses)}), nunca
 * somando mês sobre mês — assim uma matrícula de 31/01 cobre 31/01→28/02→31/03, sem "encolher".
 */
public final class CalendarioDePeriodos {

    private CalendarioDePeriodos() {}

    public static int meses(String periodicidade) {
        return switch (periodicidade) {
            case "MENSAL" -> 1;
            case "TRIMESTRAL" -> 3;
            case "SEMESTRAL" -> 6;
            case "ANUAL" -> 12;
            default -> throw new IllegalArgumentException("Periodicidade desconhecida: " + periodicidade);
        };
    }

    /** @throws IllegalArgumentException se {@code inicio} não for um limite do calendário dessa âncora */
    public static Periodo periodoQueComecaEm(LocalDate dataBase, int meses, LocalDate inicio) {
        if (!inicio.isBefore(dataBase)) {
            long aproximado = ChronoUnit.MONTHS.between(dataBase, inicio) / meses;
            for (long n = Math.max(0, aproximado - 1); n <= aproximado + 1; n++) {
                if (dataBase.plusMonths(n * meses).equals(inicio)) {
                    return new Periodo(inicio, dataBase.plusMonths((n + 1) * meses));
                }
            }
        }
        throw new IllegalArgumentException(inicio + " não é início de período para a âncora " + dataBase);
    }
}
