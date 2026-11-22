package com.locgym.compartilhado.paginacao;

import java.util.List;
import java.util.function.Function;

/** Uma página de resultados (página começa em 0). Usada nas portas e devolvida como está pela API. */
public record Pagina<T>(List<T> itens, int pagina, int tamanho, long total) {

    public static final int TAMANHO_PADRAO = 20;
    public static final int TAMANHO_MAXIMO = 100;

    public <R> Pagina<R> mapear(Function<T, R> conversor) {
        return new Pagina<>(itens.stream().map(conversor).toList(), pagina, tamanho, total);
    }

    /** Normaliza parâmetros vindos da requisição. */
    public static int paginaValida(Integer pagina) {
        return pagina == null || pagina < 0 ? 0 : pagina;
    }

    public static int tamanhoValido(Integer tamanho) {
        return tamanho == null || tamanho < 1 ? TAMANHO_PADRAO : Math.min(tamanho, TAMANHO_MAXIMO);
    }
}
