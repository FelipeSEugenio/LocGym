package com.locgym.academia;

/** Antecedência (dias antes do vencimento para gerar a cobrança) e carência (dias de atraso até vencer a matrícula). */
public record ParametrosCobranca(int diasAntecedencia, int diasCarencia) {

    public static final ParametrosCobranca PADRAO = new ParametrosCobranca(7, 10);
}
