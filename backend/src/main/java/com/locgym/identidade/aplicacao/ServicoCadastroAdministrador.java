package com.locgym.identidade.aplicacao;

import com.locgym.identidade.ContasDeUsuario;
import com.locgym.identidade.CriadorDeAcademia;
import com.locgym.identidade.CriadorDeAcademia.NovaAcademia;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cadastro público: cria o usuário, a primeira academia e o vínculo de administrador. */
@Service
public class ServicoCadastroAdministrador {

    private final ContasDeUsuario contas;
    private final CriadorDeAcademia criadorDeAcademia;
    private final ServicoAutenticacao autenticacao;

    public ServicoCadastroAdministrador(ContasDeUsuario contas, CriadorDeAcademia criadorDeAcademia,
                                        ServicoAutenticacao autenticacao) {
        this.contas = contas;
        this.criadorDeAcademia = criadorDeAcademia;
        this.autenticacao = autenticacao;
    }

    @Transactional
    public Sessao cadastrar(String nome, String email, String senha, NovaAcademia academia) {
        UUID usuarioId = contas.criarUsuario(email, nome, senha);
        criadorDeAcademia.criarComAdministrador(academia, usuarioId, nome.strip());
        return autenticacao.sessaoPara(usuarioId);
    }
}
