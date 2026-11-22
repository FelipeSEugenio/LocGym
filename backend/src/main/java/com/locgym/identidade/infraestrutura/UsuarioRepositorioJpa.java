package com.locgym.identidade.infraestrutura;

import com.locgym.identidade.dominio.Email;
import com.locgym.identidade.dominio.Portas.UsuarioRepositorio;
import com.locgym.identidade.dominio.Usuario;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class UsuarioRepositorioJpa implements UsuarioRepositorio {

    private final UsuarioJpaDados dados;

    UsuarioRepositorioJpa(UsuarioJpaDados dados) {
        this.dados = dados;
    }

    @Override
    public Optional<Usuario> porEmail(Email email) {
        return dados.findByEmail(email.valor()).map(UsuarioJpa::paraDominio);
    }

    @Override
    public Optional<Usuario> porId(UUID id) {
        return dados.findById(id).map(UsuarioJpa::paraDominio);
    }

    @Override
    public void salvar(Usuario usuario) {
        dados.findById(usuario.id()).ifPresentOrElse(
                existente -> existente.copiar(usuario),
                () -> dados.saveAndFlush(UsuarioJpa.de(usuario)));
    }
}
