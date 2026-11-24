package com.locgym.compartilhado.erros;

/** Requisição recusada por conteúdo inválido que não é um campo de formulário (ex.: QR Code) → HTTP 400 com {@code codigo}. */
public class RequisicaoInvalidaException extends RuntimeException {

    private final String codigo;

    public RequisicaoInvalidaException(String codigo, String mensagem) {
        super(mensagem);
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}
