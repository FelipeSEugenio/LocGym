package com.locgym.identidade.infraestrutura;

import com.locgym.identidade.dominio.Portas.RefreshTokenRepositorio;
import com.locgym.identidade.dominio.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class RefreshTokenRepositorioJpa implements RefreshTokenRepositorio {

    private final RefreshTokenJpaDados dados;

    RefreshTokenRepositorioJpa(RefreshTokenJpaDados dados) {
        this.dados = dados;
    }

    @Override
    public Optional<RefreshToken> porHashComBloqueio(String tokenHash) {
        return dados.findByTokenHash(tokenHash).map(RefreshTokenJpa::paraDominio);
    }

    @Override
    public void salvar(RefreshToken token) {
        dados.findById(token.id()).ifPresentOrElse(
                existente -> existente.copiar(token),
                () -> dados.save(RefreshTokenJpa.de(token)));
    }

    @Override
    public void revogarFamilia(UUID familia, Instant agora) {
        dados.revogarFamilia(familia, agora);
    }
}
