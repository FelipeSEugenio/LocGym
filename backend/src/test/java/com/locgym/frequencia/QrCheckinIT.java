package com.locgym.frequencia;

import static com.locgym.apoio.Cenarios.comoAdmin;
import static com.locgym.apoio.Cenarios.comoAluno;
import static com.locgym.apoio.Cenarios.cpfValido;
import static com.locgym.apoio.Cenarios.dadosAluno;
import static com.locgym.apoio.Cenarios.emailUnico;
import static com.locgym.apoio.Cenarios.novoAdministrador;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.locgym.TestcontainersConfig;
import com.locgym.apoio.Cenarios.Admin;
import com.locgym.apoio.ClienteHttp;
import com.locgym.compartilhado.tenancy.ContextoTenant;
import com.locgym.compartilhado.tenancy.Perfil;
import com.locgym.compartilhado.tenancy.TenantContext;
import com.locgym.frequencia.aplicacao.ServicoTokenQr;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"locgym.outbox.relay-habilitado=false", "locgym.rotina-diaria.habilitada=false"})
@Import(TestcontainersConfig.class)
class QrCheckinIT {

    @LocalServerPort int porta;
    @Autowired JsonMapper json;
    @Autowired ServicoTokenQr servicoToken;

    ClienteHttp http;
    Admin admin;
    ClienteHttp aluno;
    String alunoId;

    @BeforeEach
    void preparar() {
        http = new ClienteHttp(porta, json);
        admin = novoAdministrador(http);
        String email = emailUnico("qr");
        alunoId = comoAdmin(http, admin).post("/api/v1/alunos", dadosAluno("Carla Souza", email, cpfValido())).texto("id");
        aluno = comoAluno(http, email, admin.academiaId());
    }

    @Test
    void alunoRecebeToken() {
        var r = aluno.get("/api/v1/eu/qr-checkin");

        assertThat(r.status()).as(r.corpo().toString()).isEqualTo(200);
        assertThat(Instant.parse(r.texto("expiraEm")))
                .isCloseTo(Instant.now().plusSeconds(60), within(5, ChronoUnit.SECONDS));
        TenantContext.definir(new ContextoTenant(admin.academiaId(), Perfil.ADMIN, true, admin.usuarioId()));
        try {
            var conteudo = servicoToken.validar(r.texto("token"));
            assertThat(conteudo.alunoId()).isEqualTo(UUID.fromString(alunoId));
            assertThat(conteudo.academiaId()).isEqualTo(admin.academiaId());
        } finally {
            TenantContext.limpar();
        }
    }

    @Test
    void adminSemPerfilAluno403() {
        assertThat(comoAdmin(http, admin).get("/api/v1/eu/qr-checkin").status()).isEqualTo(403);
    }

    @Test
    void tokensSucessivosDiferentes() {
        assertThat(aluno.get("/api/v1/eu/qr-checkin").texto("token"))
                .isNotEqualTo(aluno.get("/api/v1/eu/qr-checkin").texto("token"));
    }
}
