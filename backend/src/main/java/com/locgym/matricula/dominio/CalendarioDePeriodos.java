package com.locgym.matricula.dominio;

import java.time.LocalDate;

/**
 * Calendário de períodos com dia-âncora: cada período termina {@code meses} depois do início, no dia do mês da
 * âncora (limitado ao fim do mês) — assim uma matrícula de 31/01 cobre 31/01→28/02→31/03, sem "encolher".
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

    /**
     * Fim do período que começa em {@code inicio}: {@code meses} depois, no dia-âncora (limitado ao último dia do
     * mês). Ex.: âncora 31 → 28/02 + 1 mês = 31/03.
     */
    public static LocalDate fimDoPeriodo(LocalDate inicio, int meses, int diaAncora) {
        LocalDate mes = inicio.plusMonths(meses);
        return mes.withDayOfMonth(Math.min(diaAncora, mes.lengthOfMonth()));
    }

    public static Periodo periodoQueComecaEm(LocalDate dataBase, int meses, LocalDate inicio) {
        return new Periodo(inicio, fimDoPeriodo(inicio, meses, dataBase.getDayOfMonth()));
    }

    /** Primeiro limite de período estritamente depois de {@code data}, percorrendo o calendário desde a âncora. */
    public static LocalDate primeiroLimiteDepoisDe(LocalDate dataBase, int meses, LocalDate data) {
        LocalDate limite = dataBase;
        while (!limite.isAfter(data)) {
            limite = fimDoPeriodo(limite, meses, dataBase.getDayOfMonth());
        }
        return limite;
    }
}
