package com.locgym.aluno.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.locgym.compartilhado.erros.ValorInvalidoException;
import org.junit.jupiter.api.Test;

class CpfTest {

    @Test
    void validoSemMascara() {
        assertThat(Cpf.de("52998224725").toString()).isEqualTo("52998224725");
    }

    @Test
    void validoComMascara() {
        assertThat(Cpf.de("529.982.247-25")).isEqualTo(Cpf.de("52998224725"));
    }

    @Test
    void digitoVerificadorErrado() {
        assertThatThrownBy(() -> Cpf.de("52998224724")).isInstanceOf(ValorInvalidoException.class)
                .extracting("campo").isEqualTo("cpf");
    }

    @Test
    void sequenciaRepetida() {
        assertThatThrownBy(() -> Cpf.de("111.111.111-11")).isInstanceOf(ValorInvalidoException.class);
    }

    @Test
    void tamanhoErradoOuVazio() {
        assertThatThrownBy(() -> Cpf.de("1234567890")).isInstanceOf(ValorInvalidoException.class);
        assertThatThrownBy(() -> Cpf.de("")).isInstanceOf(ValorInvalidoException.class);
        assertThatThrownBy(() -> Cpf.de(null)).isInstanceOf(ValorInvalidoException.class);
    }
}
