package com.locgym.identidade.dominio;

import com.locgym.compartilhado.erros.ValorInvalidoException;
import java.util.Locale;
import java.util.regex.Pattern;

/** E-mail normalizado (sem espaços nas pontas e em minúsculas): é a credencial de login. */
public record Email(String valor) {

    private static final Pattern FORMATO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public Email {
        if (valor == null || valor.length() > 254 || !FORMATO.matcher(valor).matches()
                || !valor.equals(valor.strip().toLowerCase(Locale.ROOT))) {
            throw new ValorInvalidoException("email", "E-mail inválido");
        }
    }

    public static Email de(String bruto) {
        if (bruto == null) {
            throw new ValorInvalidoException("email", "E-mail inválido");
        }
        return new Email(bruto.strip().toLowerCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return valor;
    }
}
