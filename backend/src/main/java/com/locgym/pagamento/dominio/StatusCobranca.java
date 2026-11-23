package com.locgym.pagamento.dominio;

public enum StatusCobranca {
    PENDENTE, PAGO, ATRASADO, CANCELADO;

    /** Ainda pode ser paga ou abonada. */
    public boolean emAberto() {
        return this == PENDENTE || this == ATRASADO;
    }
}
