package com.locgym.identidade.aplicacao;

import com.locgym.identidade.dominio.Usuario;
import java.time.Instant;

/** Emite o access token (JWT) de um usuário. */
public interface EmissorDeTokens {

    TokenDeAcesso emitirAcesso(Usuario usuario);

    record TokenDeAcesso(String valor, Instant expiraEm) {}
}
