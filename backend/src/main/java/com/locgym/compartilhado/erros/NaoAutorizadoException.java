package com.locgym.compartilhado.erros;

/** Credenciais ou sessão inválidas → HTTP 401. */
public class NaoAutorizadoException extends RuntimeException {

    public NaoAutorizadoException(String mensagem) {
        super(mensagem);
    }
}
