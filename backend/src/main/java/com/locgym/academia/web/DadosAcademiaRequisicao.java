package com.locgym.academia.web;

import com.locgym.academia.ParametrosCobranca;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import com.locgym.academia.dominio.DadosDaAcademia;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

/** Dados cadastrais recebidos da API; aceita CNPJ e CEP com ou sem máscara. */
record DadosAcademiaRequisicao(
        @NotBlank @Size(max = 150) String nome,
        @NotBlank @Pattern(regexp = "\\d{2}\\.?\\d{3}\\.?\\d{3}/?\\d{4}-?\\d{2}", message = "CNPJ inválido") String cnpj,
        @NotBlank @Size(max = 200) String endereco,
        @NotBlank @Size(max = 100) String cidade,
        @NotBlank @Pattern(regexp = "[A-Za-z]{2}", message = "UF inválida") String estado,
        @NotBlank @Pattern(regexp = "\\d{5}-?\\d{3}", message = "CEP inválido") String cep,
        @NotBlank @Size(max = 20) String telefone,
        @NotBlank @Size(max = 254) String email,
        @Min(value = 1, message = "Informe de 1 a 30 dias") @Max(value = 30, message = "Informe de 1 a 30 dias")
        Integer diasAntecedenciaCobranca,
        @Min(value = 0, message = "Informe de 0 a 60 dias") @Max(value = 60, message = "Informe de 0 a 60 dias")
        Integer diasCarencia) {

    /** Nulo quando a requisição não traz os dois parâmetros (mantém os atuais). */
    ParametrosCobranca parametrosCobranca() {
        return diasAntecedenciaCobranca == null || diasCarencia == null ? null
                : new ParametrosCobranca(diasAntecedenciaCobranca, diasCarencia);
    }

    DadosDaAcademia paraDominio() {
        return new DadosDaAcademia(nome.strip(), cnpj.replaceAll("\\D", ""), endereco.strip(), cidade.strip(),
                estado.toUpperCase(Locale.ROOT), cep.replaceAll("\\D", ""), telefone.strip(),
                email.strip().toLowerCase(Locale.ROOT));
    }
}
