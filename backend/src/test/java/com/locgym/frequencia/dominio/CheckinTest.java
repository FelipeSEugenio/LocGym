package com.locgym.frequencia.dominio;

import static java.time.LocalTime.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.locgym.compartilhado.erros.RegraVioladaException;
import com.locgym.compartilhado.erros.ValorInvalidoException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CheckinTest {

    static final LocalDate DIA = LocalDate.of(2026, 11, 23);
    static final Instant AGORA = Instant.parse("2026-11-23T22:10:00Z");
    final UUID aluno = UUID.randomUUID();
    final UUID aula = UUID.randomUUID();
    final UUID admin = UUID.randomUUID();

    Checkin registrar(LocalTime hora) {
        return Checkin.registrar(aluno, aula, of(19, 0), DIA, hora, Origem.QR, 10, null, AGORA);
    }

    @Test
    void registrarTruncaSegundos() {
        Checkin c = registrar(LocalTime.of(19, 10, 59));
        assertThat(c.hora()).isEqualTo(of(19, 10));
        assertThat(c.pontualidade()).isEqualTo(Pontualidade.PONTUAL);
        assertThat(c.status()).isEqualTo(StatusCheckin.ATIVO);
        assertThat(c.origem()).isEqualTo(Origem.QR);
        assertThat(c.aulaId()).isEqualTo(aula);
        assertThat(c.alunoId()).isEqualTo(aluno);
        assertThat(c.data()).isEqualTo(DIA);
        assertThat(c.registradoEm()).isEqualTo(AGORA);
        assertThat(c.motivo()).isNull();
    }

    @Test
    void atrasado() {
        assertThat(registrar(of(19, 11)).pontualidade()).isEqualTo(Pontualidade.ATRASADO);
    }

    @Test
    void livreSemPontualidade() {
        Checkin c = Checkin.registrar(aluno, null, null, DIA, of(15, 0), Origem.MANUAL, 10, admin, AGORA);
        assertThat(c.livre()).isTrue();
        assertThat(c.pontualidade()).isNull();
        assertThat(c.registradoPor()).isEqualTo(admin);
    }

    @Test
    void excluirExigeMotivo() {
        Checkin c = registrar(of(19, 5));
        assertThatThrownBy(() -> c.excluir(" ")).isInstanceOf(ValorInvalidoException.class)
                .extracting("campo").isEqualTo("motivo");
        assertThatThrownBy(() -> c.excluir("x".repeat(301))).isInstanceOf(ValorInvalidoException.class);
        c.excluir("  lançado errado ");
        assertThat(c.status()).isEqualTo(StatusCheckin.EXCLUIDO);
        assertThat(c.motivo()).isEqualTo("lançado errado");
    }

    @Test
    void excluirSoDeAtivo() {
        Checkin c = registrar(of(19, 5));
        c.excluir("erro");
        assertThatThrownBy(() -> c.excluir("de novo")).isInstanceOf(RegraVioladaException.class)
                .extracting("codigo").isEqualTo("CHECKIN_NAO_ATIVO");
    }

    @Test
    void corrigirCriaNovoEMarcaAntigo() {
        Checkin antigo = registrar(of(19, 5));
        UUID outraAula = UUID.randomUUID();
        Instant depois = AGORA.plusSeconds(600);

        Checkin novo = antigo.corrigir(outraAula, of(20, 0), DIA, of(20, 20), " aula errada ", 10, admin, depois);

        assertThat(antigo.status()).isEqualTo(StatusCheckin.CORRIGIDO);
        assertThat(antigo.motivo()).isEqualTo("aula errada");
        assertThat(antigo.substituidoPor()).isEqualTo(novo.id());
        assertThat(novo.status()).isEqualTo(StatusCheckin.ATIVO);
        assertThat(novo.origem()).isEqualTo(Origem.QR);
        assertThat(novo.aulaId()).isEqualTo(outraAula);
        assertThat(novo.pontualidade()).isEqualTo(Pontualidade.ATRASADO);
        assertThat(novo.registradoPor()).isEqualTo(admin);
        assertThat(novo.registradoEm()).isEqualTo(depois);
        assertThat(novo.id()).isNotEqualTo(antigo.id());
        assertThatThrownBy(() -> antigo.corrigir(aula, of(19, 0), DIA, of(19, 0), "x", 10, admin, depois))
                .isInstanceOf(RegraVioladaException.class);
    }

    @Test
    void corrigirExigeMotivoSemAlterar() {
        Checkin c = registrar(of(19, 5));
        assertThatThrownBy(() -> c.corrigir(aula, of(19, 0), DIA, of(19, 6), "", 10, admin, AGORA))
                .isInstanceOf(ValorInvalidoException.class);
        assertThat(c.status()).isEqualTo(StatusCheckin.ATIVO);
    }

    @Test
    void validarMomento() {
        LocalDateTime agora = LocalDateTime.of(2026, 11, 23, 19, 0);
        Checkin.validarMomento(DIA.minusDays(30), of(10, 0), agora);
        Checkin.validarMomento(DIA, of(19, 0), agora);
        assertThatThrownBy(() -> Checkin.validarMomento(DIA.minusDays(31), of(10, 0), agora))
                .isInstanceOf(ValorInvalidoException.class).extracting("campo").isEqualTo("data");
        assertThatThrownBy(() -> Checkin.validarMomento(DIA, of(19, 1), agora))
                .isInstanceOf(ValorInvalidoException.class).extracting("campo").isEqualTo("hora");
        assertThatThrownBy(() -> Checkin.validarMomento(DIA.plusDays(1), of(0, 0), agora))
                .isInstanceOf(ValorInvalidoException.class);
    }
}
