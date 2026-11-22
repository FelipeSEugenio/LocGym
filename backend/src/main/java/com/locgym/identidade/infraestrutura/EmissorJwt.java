package com.locgym.identidade.infraestrutura;

import com.locgym.identidade.aplicacao.EmissorDeTokens;
import com.locgym.identidade.dominio.Usuario;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
class EmissorJwt implements EmissorDeTokens {

    static final Duration VALIDADE = Duration.ofMinutes(15);
    static final String CLAIM_NOME = "nome";
    static final String CLAIM_DEVE_TROCAR_SENHA = "deve_trocar_senha";

    private final JwtEncoder encoder;
    private final Clock clock;

    EmissorJwt(JwtEncoder encoder, Clock clock) {
        this.encoder = encoder;
        this.clock = clock;
    }

    @Override
    public TokenDeAcesso emitirAcesso(Usuario usuario) {
        Instant agora = clock.instant();
        Instant expira = agora.plus(VALIDADE);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("locgym")
                .subject(usuario.id().toString())
                .issuedAt(agora)
                .expiresAt(expira)
                .claim(CLAIM_NOME, usuario.nome())
                .claim(CLAIM_DEVE_TROCAR_SENHA, usuario.deveTrocarSenha())
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
        return new TokenDeAcesso(token, expira);
    }
}
