package com.locgym.compartilhado.erros;

/** Autenticado, mas sem permissão para esta operação → HTTP 403 com {@code codigo}. */
public class AcessoNegadoException extends RuntimeException {

    private final String codigo;

    public AcessoNegadoException(String codigo, String mensagem) {
        super(mensagem);
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}
