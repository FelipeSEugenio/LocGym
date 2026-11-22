package com.locgym.identidade.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EmailTest {

    @Test
    void normalizaEspacosEMaiusculas() {
        assertThat(Email.de("  Ana.Souza@Exemplo.COM ").valor()).isEqualTo("ana.souza@exemplo.com");
    }

    @Test
    void doisEmailsNormalizadosSaoIguais() {
        assertThat(Email.de("ANA@x.com")).isEqualTo(Email.de("ana@X.com "));
    }

    @Test
    void rejeitaFormatoInvalido() {
        assertThatThrownBy(() -> Email.de("sem-arroba")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Email.de("a@b")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Email.de(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Email.de(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
