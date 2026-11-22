package com.locgym.plano.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.locgym.compartilhado.erros.ValorInvalidoException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PlanoTest {

    @Test
    void novoPlanoComecaAtivo() {
        Plano p = Plano.novo("Mensal", null, new BigDecimal("150.00"), Periodicidade.MENSAL);
        assertThat(p.status()).isEqualTo(StatusPlano.ATIVO);
        assertThat(p.nome()).isEqualTo("Mensal");
    }

    @Test
    void valorZeroOuNegativoRecusado() {
        assertThatThrownBy(() -> Plano.novo("X", null, BigDecimal.ZERO, Periodicidade.MENSAL))
                .isInstanceOf(ValorInvalidoException.class).extracting("campo").isEqualTo("valor");
        assertThatThrownBy(() -> Plano.novo("X", null, new BigDecimal("-1"), Periodicidade.MENSAL))
                .isInstanceOf(ValorInvalidoException.class);
    }

    @Test
    void maisDeDuasCasasDecimaisRecusado() {
        assertThatThrownBy(() -> Plano.novo("X", null, new BigDecimal("10.001"), Periodicidade.MENSAL))
                .isInstanceOf(ValorInvalidoException.class).extracting("campo").isEqualTo("valor");
    }

    @Test
    void nomeEmBrancoRecusado() {
        assertThatThrownBy(() -> Plano.novo("  ", null, BigDecimal.TEN, Periodicidade.MENSAL))
                .isInstanceOf(ValorInvalidoException.class).extracting("campo").isEqualTo("nome");
    }

    @Test
    void alterarStatusInformaSeMudou() {
        Plano p = Plano.novo("Mensal", null, BigDecimal.TEN, Periodicidade.MENSAL);
        assertThat(p.alterarStatus(StatusPlano.ATIVO)).isFalse();
        assertThat(p.alterarStatus(StatusPlano.INATIVO)).isTrue();
        assertThat(p.status()).isEqualTo(StatusPlano.INATIVO);
    }
}
