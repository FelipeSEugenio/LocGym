package com.locgym.frequencia.dominio;

import java.time.LocalTime;

public enum Pontualidade {
    PONTUAL,
    ATRASADO;

    /** PONTUAL se a hora não passa do início mais a tolerância (inclusive); chegar antes é pontual. */
    public static Pontualidade de(LocalTime inicioAula, LocalTime hora, int toleranciaMin) {
        return hora.isAfter(inicioAula.plusMinutes(toleranciaMin)) ? ATRASADO : PONTUAL;
    }
}
