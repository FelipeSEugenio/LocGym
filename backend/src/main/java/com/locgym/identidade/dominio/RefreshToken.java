package com.locgym.identidade.dominio;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Refresh token rotativo. Só o hash é guardado. Tokens de uma mesma sessão formam uma família:
 * reuso de um token já usado (fora da janela de tolerância) indica roubo e revoga a família.
 */
public class RefreshToken {

    /** Reuso dentro desta janela é tratado como corrida entre abas/requisições, não como roubo. */
    public static final Duration JANELA_DE_TOLERANCIA = Duration.ofSeconds(10);

    public enum Avaliacao { PRIMEIRO_USO, REUSO_TOLERADO, REUSO_SUSPEITO, INVALIDO }

    private final UUID id;
    private final UUID usuarioId;
    private final String tokenHash;
    private final UUID familia;
    private final Instant expiraEm;
    private Instant usadoEm;
    private Instant revogadoEm;

    public RefreshToken(UUID id, UUID usuarioId, String tokenHash, UUID familia, Instant expiraEm,
                        Instant usadoEm, Instant revogadoEm) {
        this.id = Objects.requireNonNull(id);
        this.usuarioId = Objects.requireNonNull(usuarioId);
        this.tokenHash = Objects.requireNonNull(tokenHash);
        this.familia = Objects.requireNonNull(familia);
        this.expiraEm = Objects.requireNonNull(expiraEm);
        this.usadoEm = usadoEm;
        this.revogadoEm = revogadoEm;
    }

    public static RefreshToken novo(UUID usuarioId, String tokenHash, UUID familia, Instant agora, Duration validade) {
        return new RefreshToken(UUID.randomUUID(), usuarioId, tokenHash, familia, agora.plus(validade), null, null);
    }

    public Avaliacao avaliarUso(Instant agora) {
        if (revogadoEm != null || !agora.isBefore(expiraEm)) {
            return Avaliacao.INVALIDO;
        }
        if (usadoEm == null) {
            return Avaliacao.PRIMEIRO_USO;
        }
        return Duration.between(usadoEm, agora).compareTo(JANELA_DE_TOLERANCIA) <= 0
                ? Avaliacao.REUSO_TOLERADO
                : Avaliacao.REUSO_SUSPEITO;
    }

    public void marcarUsado(Instant agora) {
        if (usadoEm == null) {
            usadoEm = agora;
        }
    }

    public void revogar(Instant agora) {
        if (revogadoEm == null) {
            revogadoEm = agora;
        }
    }

    public UUID id() { return id; }
    public UUID usuarioId() { return usuarioId; }
    public String tokenHash() { return tokenHash; }
    public UUID familia() { return familia; }
    public Instant expiraEm() { return expiraEm; }
    public Instant usadoEm() { return usadoEm; }
    public Instant revogadoEm() { return revogadoEm; }
}
