package com.locgym.compartilhado.erros;

/** Recurso inexistente — ou de outra academia, que é indistinguível de inexistente → HTTP 404. */
public class NaoEncontradoException extends RuntimeException {

    public NaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
