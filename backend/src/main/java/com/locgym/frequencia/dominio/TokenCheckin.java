package com.locgym.frequencia.dominio;

import com.locgym.compartilhado.erros.RequisicaoInvalidaException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Token do QR Code de check-in: {@code base64url(payload).base64url(HMAC-SHA256(payload))}, sem padding.
 * Payload de 48 bytes: aluno (16), academia (16), emissão em segundos (8), nonce (8). Vale 60 s; emissão mais de 5 s
 * no futuro é inválida.
 */
public final class TokenCheckin {

    public static final long VALIDADE_SEGUNDOS = 60;
    static final long TOLERANCIA_FUTURO_SEGUNDOS = 5;
    private static final int TAMANHO_PAYLOAD = 48;
    private static final String ALGORITMO = "HmacSHA256";

    public record Conteudo(UUID alunoId, UUID academiaId, Instant emitidoEm) {}

    private final SecretKeySpec chave;

    public TokenCheckin(byte[] chave) {
        if (chave == null || chave.length < 32) {
            throw new IllegalArgumentException("A chave do QR precisa de pelo menos 32 bytes");
        }
        this.chave = new SecretKeySpec(chave.clone(), ALGORITMO);
    }

    public String emitir(UUID alunoId, UUID academiaId, Instant agora, byte[] nonce8) {
        Objects.requireNonNull(nonce8);
        if (nonce8.length != 8) {
            throw new IllegalArgumentException("O nonce tem 8 bytes");
        }
        byte[] payload = ByteBuffer.allocate(TAMANHO_PAYLOAD)
                .putLong(alunoId.getMostSignificantBits()).putLong(alunoId.getLeastSignificantBits())
                .putLong(academiaId.getMostSignificantBits()).putLong(academiaId.getLeastSignificantBits())
                .putLong(agora.getEpochSecond()).put(nonce8).array();
        var b64 = Base64.getUrlEncoder().withoutPadding();
        return b64.encodeToString(payload) + "." + b64.encodeToString(assinar(payload));
    }

    public Conteudo validar(String token, Instant agora) {
        byte[] payload;
        byte[] assinatura;
        try {
            int ponto = token == null ? -1 : token.indexOf('.');
            if (ponto < 0 || token.indexOf('.', ponto + 1) >= 0) {
                throw invalido();
            }
            var b64 = Base64.getUrlDecoder();
            payload = b64.decode(token.substring(0, ponto));
            assinatura = b64.decode(token.substring(ponto + 1));
        } catch (IllegalArgumentException e) {
            throw invalido();
        }
        if (payload.length != TAMANHO_PAYLOAD || !MessageDigest.isEqual(assinar(payload), assinatura)) {
            throw invalido();
        }
        ByteBuffer b = ByteBuffer.wrap(payload);
        UUID aluno = new UUID(b.getLong(), b.getLong());
        UUID academia = new UUID(b.getLong(), b.getLong());
        Instant emitidoEm = Instant.ofEpochSecond(b.getLong());
        if (emitidoEm.isAfter(agora.plusSeconds(TOLERANCIA_FUTURO_SEGUNDOS))) {
            throw invalido();
        }
        if (agora.isAfter(emitidoEm.plusSeconds(VALIDADE_SEGUNDOS))) {
            throw new RequisicaoInvalidaException("QR_EXPIRADO", "QR Code expirado");
        }
        return new Conteudo(aluno, academia, emitidoEm);
    }

    private byte[] assinar(byte[] payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(chave);
            return mac.doFinal(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static RequisicaoInvalidaException invalido() {
        return new RequisicaoInvalidaException("QR_INVALIDO", "QR Code inválido");
    }
}
