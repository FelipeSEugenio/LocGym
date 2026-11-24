package com.locgym.agenda.dominio;

import static java.time.LocalTime.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.locgym.compartilhado.erros.ValorInvalidoException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HorarioTest {

    final UUID professor = UUID.randomUUID();

    @Test
    void fimAntesDoInicio400() {
        assertThatThrownBy(() -> Horario.novo(professor, 1, of(19, 0), of(19, 0), "Gi"))
                .isInstanceOf(ValorInvalidoException.class).extracting("campo").isEqualTo("fim");
        assertThatThrownBy(() -> Horario.novo(professor, 1, of(19, 0), of(18, 0), "Gi"))
                .isInstanceOf(ValorInvalidoException.class).extracting("campo").isEqualTo("fim");
    }

    @Test
    void diaSemanaForaDaFaixa400() {
        assertThatThrownBy(() -> Horario.novo(professor, 0, of(19, 0), of(20, 0), "Gi"))
                .isInstanceOf(ValorInvalidoException.class).extracting("campo").isEqualTo("diaSemana");
        assertThatThrownBy(() -> Horario.novo(professor, 8, of(19, 0), of(20, 0), "Gi"))
                .isInstanceOf(ValorInvalidoException.class).extracting("campo").isEqualTo("diaSemana");
    }

    @Test
    void tipoVazio400() {
        assertThatThrownBy(() -> Horario.novo(professor, 1, of(19, 0), of(20, 0), "  "))
                .isInstanceOf(ValorInvalidoException.class).extracting("campo").isEqualTo("tipo");
        assertThatThrownBy(() -> Horario.novo(professor, 1, of(19, 0), of(20, 0), "x".repeat(61)))
                .isInstanceOf(ValorInvalidoException.class).extracting("campo").isEqualTo("tipo");
    }

    @Test
    void tipoComEspacosEAparado() {
        assertThat(Horario.novo(professor, 1, of(19, 0), of(20, 0), "  Gi ").tipo()).isEqualTo("Gi");
    }

    @Test
    void segundosZerados() {
        var h = Horario.novo(professor, 1, of(19, 0, 30), of(20, 0, 59), "Gi");
        assertThat(h.inicio()).isEqualTo(of(19, 0));
        assertThat(h.fim()).isEqualTo(of(20, 0));
    }

    @Test
    void novoNasceAtivo() {
        var h = Horario.novo(professor, 3, of(7, 0), of(8, 0), "No-Gi");
        assertThat(h.status()).isEqualTo(StatusHorario.ATIVO);
        assertThat(h.ativo()).isTrue();
        assertThat(h.diaSemana()).isEqualTo(3);
        assertThat(h.professorId()).isEqualTo(professor);
    }

    @Test
    void alterarInativa() {
        var h = Horario.novo(professor, 1, of(19, 0), of(20, 0), "Gi");
        UUID outro = UUID.randomUUID();
        h.alterar(outro, 2, of(20, 0), of(21, 0), "Kids", StatusHorario.INATIVO);

        assertThat(h.ativo()).isFalse();
        assertThat(h.professorId()).isEqualTo(outro);
        assertThat(h.diaSemana()).isEqualTo(2);
        assertThat(h.inicio()).isEqualTo(of(20, 0));
        assertThat(h.tipo()).isEqualTo("Kids");
    }

    @Test
    void alterarValida() {
        var h = Horario.novo(professor, 1, of(19, 0), of(20, 0), "Gi");
        assertThatThrownBy(() -> h.alterar(professor, 1, of(21, 0), of(20, 0), "Gi", StatusHorario.ATIVO))
                .isInstanceOf(ValorInvalidoException.class);
        assertThat(h.inicio()).isEqualTo(of(19, 0));
    }
}
