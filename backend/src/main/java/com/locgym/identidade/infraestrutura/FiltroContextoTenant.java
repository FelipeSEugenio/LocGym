package com.locgym.identidade.infraestrutura;

import com.locgym.compartilhado.tenancy.ContextoTenant;
import com.locgym.compartilhado.tenancy.Perfil;
import com.locgym.compartilhado.tenancy.TenantContext;
import com.locgym.compartilhado.web.RespostaDeProblema;
import com.locgym.identidade.dominio.Portas.VinculosRepositorio;
import com.locgym.identidade.dominio.Vinculo;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Define a academia e o perfil ativos de cada requisição de tenant a partir de {@code X-Academia-Id} e
 * {@code X-Perfil}, conferindo o vínculo no banco a cada requisição (remoções valem na hora).
 * O contexto é sempre limpo ao fim da requisição.
 */
class FiltroContextoTenant extends OncePerRequestFilter {

    static final String HEADER_ACADEMIA = "X-Academia-Id";
    static final String HEADER_PERFIL = "X-Perfil";
    private static final Set<String> METODOS_DE_LEITURA = Set.of("GET", "HEAD", "OPTIONS");

    private final VinculosRepositorio vinculos;
    private final JsonMapper json;

    FiltroContextoTenant(VinculosRepositorio vinculos, JsonMapper json) {
        this.vinculos = vinculos;
        this.json = json;
    }

    /** Rotas sem tenant: autenticação, "minhas academias" e criação de nova academia. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        String caminho = req.getRequestURI();
        String metodo = req.getMethod();
        return !caminho.startsWith("/api/v1/")
                || caminho.startsWith("/api/v1/auth/")
                || ("GET".equals(metodo) && "/api/v1/eu/vinculos".equals(caminho))
                || ("POST".equals(metodo) && "/api/v1/academias".equals(caminho));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken autenticacao)) {
            chain.doFilter(req, res);   // sem autenticação: a autorização responde 401
            return;
        }
        Optional<UUID> academiaId = uuid(req.getHeader(HEADER_ACADEMIA));
        Optional<Perfil> perfil = perfil(req.getHeader(HEADER_PERFIL));
        if (academiaId.isEmpty() || perfil.isEmpty()) {
            RespostaDeProblema.escrever(res, json, HttpStatus.BAD_REQUEST, "Contexto ausente",
                    "Informe a academia e o perfil ativos (X-Academia-Id e X-Perfil).", "CONTEXTO_AUSENTE");
            return;
        }
        UUID usuarioId = UUID.fromString(autenticacao.getToken().getSubject());
        Optional<Vinculo> vinculo = vinculos.doUsuario(usuarioId).stream()
                .filter(v -> v.academiaId().equals(academiaId.get()) && v.perfil() == perfil.get())
                .findFirst();
        if (vinculo.isEmpty()) {
            RespostaDeProblema.escrever(res, json, HttpStatus.FORBIDDEN, "Acesso negado",
                    "Você não tem acesso a esta academia com este perfil.", "SEM_VINCULO");
            return;
        }
        if (!vinculo.get().academiaAtiva() && !METODOS_DE_LEITURA.contains(req.getMethod())
                && !reativacaoDaAcademia(req)) {
            RespostaDeProblema.escrever(res, json, HttpStatus.CONFLICT, "Academia inativa",
                    "Academia inativa: somente leitura.", "ACADEMIA_INATIVA");
            return;
        }

        var autoridades = new ArrayList<GrantedAuthority>(autenticacao.getAuthorities());
        autoridades.add(new SimpleGrantedAuthority("ROLE_" + perfil.get().name()));
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(autenticacao.getToken(), autoridades, autenticacao.getName()));
        TenantContext.definir(new ContextoTenant(academiaId.get(), perfil.get(), vinculo.get().academiaAtiva(), usuarioId));
        try {
            chain.doFilter(req, res);
        } finally {
            TenantContext.limpar();
        }
    }

    private static boolean reativacaoDaAcademia(HttpServletRequest req) {
        return "PATCH".equals(req.getMethod()) && "/api/v1/academia/status".equals(req.getRequestURI());
    }

    private static Optional<UUID> uuid(String valor) {
        try {
            return valor == null ? Optional.empty() : Optional.of(UUID.fromString(valor));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static Optional<Perfil> perfil(String valor) {
        try {
            return valor == null ? Optional.empty()
                    : Optional.of(Perfil.valueOf(valor)).filter(p -> p != Perfil.SISTEMA);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
