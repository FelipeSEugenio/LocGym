package com.locgym.academia.dominio;

/** Dados cadastrais de uma academia, já normalizados (CNPJ/CEP só dígitos, UF maiúscula). */
public record DadosDaAcademia(String nome, String cnpj, String endereco, String cidade, String estado,
                              String cep, String telefone, String email) {}
