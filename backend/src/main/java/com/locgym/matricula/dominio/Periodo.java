package com.locgym.matricula.dominio;

import java.time.LocalDate;
import java.util.Objects;

/** Período de vigência {@code [inicio, fim)} — fim exclusivo. */
public record Periodo(LocalDate inicio, LocalDate fim) {

    public Periodo {
        Objects.requireNonNull(inicio);
        Objects.requireNonNull(fim);
        if (!fim.isAfter(inicio)) {
            throw new IllegalArgumentException("Período vazio: " + inicio + " a " + fim);
        }
    }

    public Periodo deslocado(int dias) {
        return new Periodo(inicio.plusDays(dias), fim.plusDays(dias));
    }
}
