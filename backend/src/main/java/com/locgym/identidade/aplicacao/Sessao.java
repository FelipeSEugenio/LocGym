package com.locgym.identidade.aplicacao;

import com.locgym.identidade.aplicacao.EmissorDeTokens.TokenDeAcesso;
import com.locgym.identidade.dominio.Usuario;
import com.locgym.identidade.dominio.Vinculo;
import java.util.List;

/** Resultado de login, cadastro ou renovação: token de acesso, usuário e seus vínculos. */
public record Sessao(TokenDeAcesso acesso, Usuario usuario, List<Vinculo> vinculos) {}
