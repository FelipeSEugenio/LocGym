package com.locgym.identidade.aplicacao;

import com.locgym.compartilhado.erros.NaoAutorizadoException;
import com.locgym.identidade.dominio.Portas.RefreshTokenRepositorio;
import com.locgym.identidade.dominio.RefreshToken;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ciclo de vida do refresh token: emissão, rotação com detecção de reuso e encerramento. */
@Service
public class ServicoSessao {

    public static final Duration VALIDADE = Duration.ofDays(7);
    static final String SESSAO_INVALIDA = "Sessão expirada. Entre novamente.";

    private static final Logger log = LoggerFactory.getLogger(ServicoSessao.class);

    private final RefreshTokenRepositorio tokens;
    private final Clock clock;
    private final SecureRandom aleatorio = new SecureRandom();

    public ServicoSessao(RefreshTokenRepositorio tokens, Clock clock) {
        this.tokens = tokens;
        this.clock = clock;
    }

    public record Renovacao(UUID usuarioId, String novoValor) {}

    /** Inicia uma sessão (nova família) e devolve o valor bruto do refresh token. */
    @Transactional
    public String iniciar(UUID usuarioId) {
        return emitir(usuarioId, UUID.randomUUID());
    }

    /** Rotaciona o refresh token. A revogação por reuso suspeito é gravada mesmo com a resposta 401. */
    @Transactional(noRollbackFor = NaoAutorizadoException.class)
    public Renovacao renovar(String valor) {
        Instant agora = clock.instant();
        RefreshToken atual = buscar(valor);
        switch (atual.avaliarUso(agora)) {
            case PRIMEIRO_USO, REUSO_TOLERADO -> {
                atual.marcarUsado(agora);
                tokens.salvar(atual);
                return new Renovacao(atual.usuarioId(), emitir(atual.usuarioId(), atual.familia()));
            }
            case REUSO_SUSPEITO -> {
                log.warn("Reuso de refresh token fora da janela de tolerância: família {} revogada", atual.familia());
                tokens.revogarFamilia(atual.familia(), agora);
                throw new NaoAutorizadoException(SESSAO_INVALIDA);
            }
            default -> throw new NaoAutorizadoException(SESSAO_INVALIDA);
        }
    }

    /** Derruba todas as sessões do usuário (ex.: troca de senha — quem conhecia a senha antiga perde o acesso). */
    @Transactional
    public void revogarTodas(UUID usuarioId) {
        tokens.revogarDoUsuario(usuarioId, clock.instant());
    }

    @Transactional
    public void encerrar(String valor) {
        if (valor == null || valor.isBlank()) {
            return;
        }
        tokens.porHashComBloqueio(hash(valor)).ifPresent(t -> tokens.revogarFamilia(t.familia(), clock.instant()));
    }

    private RefreshToken buscar(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new NaoAutorizadoException(SESSAO_INVALIDA);
        }
        return tokens.porHashComBloqueio(hash(valor)).orElseThrow(() -> new NaoAutorizadoException(SESSAO_INVALIDA));
    }

    private String emitir(UUID usuarioId, UUID familia) {
        byte[] bytes = new byte[32];
        aleatorio.nextBytes(bytes);
        String valor = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.salvar(RefreshToken.novo(usuarioId, hash(valor), familia, clock.instant(), VALIDADE));
        return valor;
    }

    static String hash(String valor) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
