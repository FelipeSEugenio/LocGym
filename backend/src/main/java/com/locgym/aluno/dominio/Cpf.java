package com.locgym.aluno.dominio;

import com.locgym.compartilhado.erros.ValorInvalidoException;

/** CPF válido (dígitos verificadores conferidos), guardado só com os 11 dígitos. */
public record Cpf(String digitos) {

    public Cpf {
        if (!valido(digitos)) {
            throw new ValorInvalidoException("cpf", "CPF inválido");
        }
    }

    /** Aceita com ou sem máscara. */
    public static Cpf de(String bruto) {
        if (bruto == null) {
            throw new ValorInvalidoException("cpf", "CPF inválido");
        }
        return new Cpf(bruto.replaceAll("[.\\-\\s]", ""));
    }

    private static boolean valido(String d) {
        if (d == null || !d.matches("\\d{11}") || d.chars().distinct().count() == 1) {
            return false;
        }
        return digitoVerificador(d, 9) == d.charAt(9) - '0' && digitoVerificador(d, 10) == d.charAt(10) - '0';
    }

    private static int digitoVerificador(String d, int posicao) {
        int soma = 0;
        for (int i = 0; i < posicao; i++) {
            soma += (d.charAt(i) - '0') * (posicao + 1 - i);
        }
        return (soma * 10) % 11 % 10;
    }

    @Override
    public String toString() {
        return digitos;
    }
}
