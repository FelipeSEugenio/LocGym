package com.locgym.identidade.aplicacao;

import com.locgym.compartilhado.erros.RegraVioladaException;
import com.locgym.identidade.ContasDeUsuario;
import com.locgym.identidade.dominio.Email;
import com.locgym.identidade.dominio.Portas.CodificadorDeSenha;
import com.locgym.identidade.dominio.Portas.UsuarioRepositorio;
import com.locgym.identidade.dominio.Usuario;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ServicoContas implements ContasDeUsuario {

    private final UsuarioRepositorio usuarios;
    private final CodificadorDeSenha codificador;

    ServicoContas(UsuarioRepositorio usuarios, CodificadorDeSenha codificador) {
        this.usuarios = usuarios;
        this.codificador = codificador;
    }

    @Override
    @Transactional
    public UUID criarUsuario(String email, String nome, String senha) {
        Email normalizado = Email.de(email);
        if (usuarios.porEmail(normalizado).isPresent()) {
            throw new RegraVioladaException("EMAIL_JA_CADASTRADO", "E-mail já cadastrado");
        }
        Usuario novo = Usuario.novo(normalizado, nome, codificador.codificar(senha), false);
        usuarios.salvar(novo);
        return novo.id();
    }

    @Override
    @Transactional
    public UUID garantirUsuario(String email, String nome, String senhaInicial) {
        Email normalizado = Email.de(email);
        return usuarios.porEmail(normalizado).map(Usuario::id).orElseGet(() -> {
            Usuario novo = Usuario.novo(normalizado, nome, codificador.codificar(senhaInicial), true);
            usuarios.salvar(novo);
            return novo.id();
        });
    }
}
