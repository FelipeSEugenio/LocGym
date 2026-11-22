package com.locgym.identidade.infraestrutura;

import com.locgym.compartilhado.web.RespostaDeProblema;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/** Enquanto a senha inicial não for trocada, só as rotas de troca, logout e refresh ficam liberadas. */
class FiltroTrocaDeSenha extends OncePerRequestFilter {

    static final String CODIGO = "TROCA_DE_SENHA_OBRIGATORIA";
    private static final Set<String> LIBERADAS =
            Set.of("/api/v1/auth/trocar-senha", "/api/v1/auth/logout", "/api/v1/auth/refresh");

    private final JsonMapper json;

    FiltroTrocaDeSenha(JsonMapper json) {
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt
                && Boolean.TRUE.equals(jwt.getToken().getClaimAsBoolean(EmissorJwt.CLAIM_DEVE_TROCAR_SENHA))
                && !LIBERADAS.contains(req.getRequestURI())) {
            RespostaDeProblema.escrever(res, json, HttpStatus.FORBIDDEN, "Troca de senha obrigatória",
                    "Defina uma nova senha para continuar.", CODIGO);
            return;
        }
        chain.doFilter(req, res);
    }
}
