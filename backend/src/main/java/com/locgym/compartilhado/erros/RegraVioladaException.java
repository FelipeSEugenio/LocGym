package com.locgym.compartilhado.erros;

/** Regra de negócio violada → HTTP 409 com {@code codigo} estável para o frontend. */
public class RegraVioladaException extends RuntimeException {

    private final String codigo;
    private final java.util.Map<String, Object> extras;

    public RegraVioladaException(String codigo, String mensagem) {
        this(codigo, mensagem, java.util.Map.of());
    }

    /** {@code extras} viram propriedades do Problem Details (ex.: a lista de aulas para escolher). */
    public RegraVioladaException(String codigo, String mensagem, java.util.Map<String, ?> extras) {
        super(mensagem);
        this.codigo = codigo;
        this.extras = java.util.Map.copyOf(extras);
    }

    public java.util.Map<String, Object> extras() {
        return extras;
    }

    public String codigo() {
        return codigo;
    }
}
