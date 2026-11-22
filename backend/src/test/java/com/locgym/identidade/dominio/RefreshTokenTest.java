package com.locgym.identidade.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import com.locgym.identidade.dominio.RefreshToken.Avaliacao;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    static final Instant T0 = Instant.parse("2026-11-21T12:00:00Z");

    RefreshToken novo() {
        return RefreshToken.novo(UUID.randomUUID(), "hash", UUID.randomUUID(), T0, Duration.ofDays(7));
    }

    @Test
    void primeiroUsoEValido() {
        assertThat(novo().avaliarUso(T0.plusSeconds(60))).isEqualTo(Avaliacao.PRIMEIRO_USO);
    }

    @Test
    void reusoDentroDaJanelaDe10sETolerado() {
        RefreshToken t = novo();
        t.marcarUsado(T0.plusSeconds(60));
        assertThat(t.avaliarUso(T0.plusSeconds(70))).isEqualTo(Avaliacao.REUSO_TOLERADO);
    }

    @Test
    void reusoForaDaJanelaESuspeito() {
        RefreshToken t = novo();
        t.marcarUsado(T0.plusSeconds(60));
        assertThat(t.avaliarUso(T0.plusSeconds(71))).isEqualTo(Avaliacao.REUSO_SUSPEITO);
    }

    @Test
    void expiradoOuRevogadoEInvalido() {
        assertThat(novo().avaliarUso(T0.plus(Duration.ofDays(7)).plusSeconds(1))).isEqualTo(Avaliacao.INVALIDO);
        RefreshToken revogado = novo();
        revogado.revogar(T0.plusSeconds(1));
        assertThat(revogado.avaliarUso(T0.plusSeconds(2))).isEqualTo(Avaliacao.INVALIDO);
    }
}
