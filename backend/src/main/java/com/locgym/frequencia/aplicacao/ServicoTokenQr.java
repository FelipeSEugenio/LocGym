package com.locgym.frequencia.aplicacao;

import com.locgym.aluno.Alunos;
import com.locgym.compartilhado.erros.NaoEncontradoException;
import com.locgym.compartilhado.erros.RequisicaoInvalidaException;
import com.locgym.compartilhado.tenancy.TenantContext;
import com.locgym.frequencia.dominio.TokenCheckin;
import com.locgym.frequencia.dominio.TokenCheckin.Conteudo;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class ServicoTokenQr {

    public record TokenEmitido(String token, Instant expiraEm) {}

    private final TokenCheckin tokens;
    private final Alunos alunos;
    private final Clock clock;
    private final SecureRandom aleatorio = new SecureRandom();

    public ServicoTokenQr(TokenCheckin tokens, Alunos alunos, Clock clock) {
        this.tokens = tokens;
        this.alunos = alunos;
        this.clock = clock;
    }

    /** Token do aluno logado na academia do contexto. */
    public TokenEmitido emitirParaUsuarioAtual() {
        var contexto = TenantContext.exigir();
        var alunoId = alunos.idDoUsuario(contexto.usuarioId())
                .orElseThrow(() -> new NaoEncontradoException("Aluno não encontrado"));
        Instant agora = clock.instant();
        byte[] nonce = new byte[8];
        aleatorio.nextBytes(nonce);
        return new TokenEmitido(tokens.emitir(alunoId, contexto.academiaId(), agora, nonce),
                agora.plusSeconds(TokenCheckin.VALIDADE_SEGUNDOS));
    }

    /** Valida o token lido na recepção da academia do contexto. */
    public Conteudo validar(String token) {
        Conteudo c = tokens.validar(token, clock.instant());
        if (!c.academiaId().equals(TenantContext.exigir().academiaId())) {
            throw new RequisicaoInvalidaException("QR_DE_OUTRA_ACADEMIA", "QR Code de outra academia");
        }
        return c;
    }
}
