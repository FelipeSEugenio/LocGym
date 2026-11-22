package com.locgym.compartilhado.erros;

/** Regra de negócio violada → HTTP 409 com {@code codigo} estável para o frontend. */
public class RegraVioladaException extends RuntimeException {

    private final String codigo;

    public RegraVioladaException(String codigo, String mensagem) {
        super(mensagem);
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}
