package com.locgym.compartilhado.erros;

/** Valor de entrada inválido detectado pelo domínio → HTTP 400 com o campo. */
public class ValorInvalidoException extends IllegalArgumentException {

    private final String campo;

    public ValorInvalidoException(String campo, String mensagem) {
        super(mensagem);
        this.campo = campo;
    }

    public String campo() {
        return campo;
    }
}
