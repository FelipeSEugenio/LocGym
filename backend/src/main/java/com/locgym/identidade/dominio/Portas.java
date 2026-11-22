package com.locgym.identidade.dominio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Portas de saída do módulo de identidade (implementadas na infraestrutura). */
public final class Portas {

    private Portas() {}

    public interface UsuarioRepositorio {
        Optional<Usuario> porEmail(Email email);
        Optional<Usuario> porId(UUID id);
        void salvar(Usuario usuario);
    }

    public interface VinculosRepositorio {
        List<Vinculo> doUsuario(UUID usuarioId);
    }

    public interface RefreshTokenRepositorio {
        /** Busca e bloqueia a linha até o fim da transação (renovações concorrentes são serializadas). */
        Optional<RefreshToken> porHashComBloqueio(String tokenHash);
        void salvar(RefreshToken token);
        void revogarFamilia(UUID familia, java.time.Instant agora);
    }

    public interface CodificadorDeSenha {
        String codificar(String senha);

        /** Compara em tempo semelhante mesmo quando não há hash (usuário inexistente). */
        boolean confere(String senha, String hashOuNulo);
    }
}
