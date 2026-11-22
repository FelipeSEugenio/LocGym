package com.locgym.identidade.aplicacao;

import com.locgym.compartilhado.erros.NaoAutorizadoException;
import com.locgym.identidade.dominio.Email;
import com.locgym.identidade.dominio.Portas.CodificadorDeSenha;
import com.locgym.identidade.dominio.Portas.UsuarioRepositorio;
import com.locgym.identidade.dominio.Portas.VinculosRepositorio;
import com.locgym.identidade.dominio.Usuario;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicoAutenticacao {

    static final String CREDENCIAIS_INVALIDAS = "E-mail ou senha inválidos";

    private final UsuarioRepositorio usuarios;
    private final VinculosRepositorio vinculos;
    private final CodificadorDeSenha codificador;
    private final EmissorDeTokens emissor;

    public ServicoAutenticacao(UsuarioRepositorio usuarios, VinculosRepositorio vinculos,
                               CodificadorDeSenha codificador, EmissorDeTokens emissor) {
        this.usuarios = usuarios;
        this.vinculos = vinculos;
        this.codificador = codificador;
        this.emissor = emissor;
    }

    /** Mesma resposta (e tempo semelhante) para e-mail inexistente e senha errada. */
    @Transactional(readOnly = true)
    public Sessao login(String email, String senha) {
        Optional<Usuario> usuario;
        try {
            usuario = usuarios.porEmail(Email.de(email));
        } catch (IllegalArgumentException formatoInvalido) {
            usuario = Optional.empty();
        }
        boolean confere = codificador.confere(senha, usuario.map(Usuario::senhaHash).orElse(null));
        if (usuario.isEmpty() || !confere) {
            throw new NaoAutorizadoException(CREDENCIAIS_INVALIDAS);
        }
        return sessaoPara(usuario.get());
    }

    /** Troca a senha (inclusive a inicial obrigatória) e devolve um access token atualizado. */
    @Transactional
    public Sessao trocarSenha(UUID usuarioId, String senhaAtual, String novaSenha) {
        Usuario usuario = usuarios.porId(usuarioId)
                .orElseThrow(() -> new NaoAutorizadoException("Sessão inválida"));
        if (!codificador.confere(senhaAtual, usuario.senhaHash())) {
            throw new NaoAutorizadoException("Senha atual incorreta");
        }
        usuario.definirSenha(codificador.codificar(novaSenha));
        usuarios.salvar(usuario);
        return sessaoPara(usuario);
    }

    @Transactional(readOnly = true)
    public Sessao sessaoPara(UUID usuarioId) {
        return sessaoPara(usuarios.porId(usuarioId)
                .orElseThrow(() -> new NaoAutorizadoException("Sessão inválida")));
    }

    private Sessao sessaoPara(Usuario usuario) {
        return new Sessao(emissor.emitirAcesso(usuario), usuario, vinculos.doUsuario(usuario.id()));
    }
}
