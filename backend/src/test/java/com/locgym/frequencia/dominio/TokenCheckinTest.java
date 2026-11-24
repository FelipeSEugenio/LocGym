package com.locgym.frequencia.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.locgym.compartilhado.erros.RequisicaoInvalidaException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TokenCheckinTest {

    static final byte[] CHAVE = "uma-chave-de-teste-com-32-bytes!".getBytes();
    static final byte[] NONCE = {1, 2, 3, 4, 5, 6, 7, 8};
    static final Instant EMISSAO = Instant.parse("2026-11-23T22:00:00Z");

    final TokenCheckin tokens = new TokenCheckin(CHAVE);
    final UUID aluno = UUID.randomUUID();
    final UUID academia = UUID.randomUUID();

    String emitir() {
        return tokens.emitir(aluno, academia, EMISSAO, NONCE);
    }

    static void codigo(Runnable r, String codigo) {
        assertThatThrownBy(r::run).isInstanceOf(RequisicaoInvalidaException.class)
                .extracting("codigo").isEqualTo(codigo);
    }

    @Test
    void emiteEValida() {
        var c = tokens.validar(emitir(), EMISSAO.plusSeconds(10));
        assertThat(c.alunoId()).isEqualTo(aluno);
        assertThat(c.academiaId()).isEqualTo(academia);
        assertThat(c.emitidoEm()).isEqualTo(EMISSAO);
    }

    @Test
    void aos60sAindaValidoEAos61sExpirado() {
        String t = emitir();
        assertThat(tokens.validar(t, EMISSAO.plusSeconds(60)).alunoId()).isEqualTo(aluno);
        codigo(() -> tokens.validar(t, EMISSAO.plusSeconds(61)), "QR_EXPIRADO");
    }

    @Test
    void emitidoNoFuturoAlem5sInvalido() {
        String t = emitir();
        assertThat(tokens.validar(t, EMISSAO.minusSeconds(5)).alunoId()).isEqualTo(aluno);
        codigo(() -> tokens.validar(t, EMISSAO.minusSeconds(6)), "QR_INVALIDO");
    }

    @Test
    void assinaturaAdulteradaInvalida() {
        String t = emitir();
        char c = t.charAt(3);
        String adulterado = t.substring(0, 3) + (c == 'A' ? 'B' : 'A') + t.substring(4);
        codigo(() -> tokens.validar(adulterado, EMISSAO), "QR_INVALIDO");
    }

    @Test
    void outraChaveInvalida() {
        var outra = new TokenCheckin("outra-chave-de-teste-com-32bytes".getBytes());
        codigo(() -> outra.validar(emitir(), EMISSAO), "QR_INVALIDO");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "a.b.c", "!!!.???", ".", "abc."})
    void tokenMalformadoInvalido(String token) {
        codigo(() -> tokens.validar(token, EMISSAO), "QR_INVALIDO");
    }

    @Test
    void payloadDeTamanhoErradoInvalido() {
        var b64 = Base64.getUrlEncoder().withoutPadding();
        String partes = b64.encodeToString(new byte[47]) + "." + b64.encodeToString(new byte[32]);
        codigo(() -> tokens.validar(partes, EMISSAO), "QR_INVALIDO");
        codigo(() -> tokens.validar(null, EMISSAO), "QR_INVALIDO");
    }

    @Test
    void chaveCurtaRejeitada() {
        assertThatThrownBy(() -> new TokenCheckin(new byte[31])).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tokenCabeEm110Caracteres() {
        assertThat(emitir()).hasSizeLessThanOrEqualTo(110);
    }
}
