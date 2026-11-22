package com.locgym.plano.dominio;

/** Periodicidade de cobrança do plano, com a quantidade de meses de cada período. */
public enum Periodicidade {
    MENSAL(1), TRIMESTRAL(3), SEMESTRAL(6), ANUAL(12);

    private final int meses;

    Periodicidade(int meses) {
        this.meses = meses;
    }

    public int meses() {
        return meses;
    }
}
