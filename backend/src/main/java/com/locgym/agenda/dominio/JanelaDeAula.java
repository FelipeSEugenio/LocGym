package com.locgym.agenda.dominio;

import java.time.LocalTime;

/** Janela de check-in de uma aula: de 30 minutos antes do início (recortada em 00:00) até o fim, exclusivo. */
public final class JanelaDeAula {

    public static final int MINUTOS_ANTES = 30;

    private JanelaDeAula() {}

    public static boolean contem(LocalTime inicio, LocalTime fim, LocalTime hora) {
        LocalTime abre = inicio.isBefore(LocalTime.of(0, MINUTOS_ANTES)) ? LocalTime.MIDNIGHT
                : inicio.minusMinutes(MINUTOS_ANTES);
        return !hora.isBefore(abre) && hora.isBefore(fim);
    }
}
