package com.locgym.identidade.web;

import com.locgym.identidade.aplicacao.Sessao;
import com.locgym.identidade.dominio.Vinculo;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Corpo devolvido por login, cadastro e renovação de sessão. */
public record SessaoResposta(String accessToken, Instant expiraEm, UsuarioResposta usuario,
                             List<VinculoResposta> vinculos) {

    public record UsuarioResposta(UUID id, String nome, String email, boolean deveTrocarSenha) {}

    public record VinculoResposta(UUID academiaId, String academiaNome, String academiaStatus, String perfil) {
        static VinculoResposta de(Vinculo v) {
            return new VinculoResposta(v.academiaId(), v.academiaNome(), v.academiaStatus(), v.perfil().name());
        }
    }

    static SessaoResposta de(Sessao s) {
        var u = s.usuario();
        return new SessaoResposta(s.acesso().valor(), s.acesso().expiraEm(),
                new UsuarioResposta(u.id(), u.nome(), u.email().valor(), u.deveTrocarSenha()),
                s.vinculos().stream().map(VinculoResposta::de).toList());
    }
}
