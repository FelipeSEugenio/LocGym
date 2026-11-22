package com.locgym.identidade.infraestrutura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.locgym.compartilhado.tenancy.ContextoTenant;
import com.locgym.compartilhado.tenancy.Perfil;
import com.locgym.compartilhado.tenancy.TenantContext;
import com.locgym.identidade.dominio.Vinculo;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.json.JsonMapper;

/** O contexto de tenant vale só durante a requisição — nunca vaza para a próxima na mesma thread. */
class FiltroContextoTenantTest {

    static final UUID USUARIO = UUID.randomUUID();
    static final UUID ACADEMIA = UUID.randomUUID();

    FiltroContextoTenant filtro = new FiltroContextoTenant(
            usuarioId -> List.of(new Vinculo(ACADEMIA, "Academia", "ATIVA", Perfil.ADMIN)), JsonMapper.builder().build());

    @BeforeEach
    void autenticar() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject(USUARIO.toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
        TenantContext.limpar();
    }

    MockHttpServletRequest requisicao() {
        var req = new MockHttpServletRequest("GET", "/api/v1/alunos");
        req.addHeader("X-Academia-Id", ACADEMIA.toString());
        req.addHeader("X-Perfil", "ADMIN");
        return req;
    }

    @Test
    void contextoExisteDuranteARequisicaoESomeDepois() throws Exception {
        AtomicReference<Optional<ContextoTenant>> visto = new AtomicReference<>();

        filtro.doFilter(requisicao(), new MockHttpServletResponse(), (req, res) -> visto.set(TenantContext.atual()));

        assertThat(visto.get()).hasValueSatisfying(c -> {
            assertThat(c.academiaId()).isEqualTo(ACADEMIA);
            assertThat(c.perfil()).isEqualTo(Perfil.ADMIN);
            assertThat(c.usuarioId()).isEqualTo(USUARIO);
        });
        assertThat(TenantContext.atual()).isEmpty();
    }

    @Test
    void contextoSomeMesmoQuandoARequisicaoFalha() {
        assertThatThrownBy(() -> filtro.doFilter(requisicao(), new MockHttpServletResponse(), (req, res) -> {
            throw new IllegalStateException("falha no meio");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(TenantContext.atual()).isEmpty();
    }
}
