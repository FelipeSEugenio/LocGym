package com.locgym.compartilhado.tenancy;

import static com.locgym.apoio.Cenarios.SENHA;
import static com.locgym.apoio.Cenarios.emailUnico;
import static com.locgym.apoio.Cenarios.novoAdministrador;
import static org.assertj.core.api.Assertions.assertThat;

import com.locgym.TestcontainersConfig;
import com.locgym.TestcontainersConfig.BancoSuperusuario;
import com.locgym.apoio.Cenarios.Admin;
import com.locgym.apoio.ClienteHttp;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "locgym.outbox.relay-habilitado=false")
@Import(TestcontainersConfig.class)
class ContextoTenantIT {

    @LocalServerPort int porta;
    @Autowired JsonMapper json;
    @Autowired BancoSuperusuario su;
    ClienteHttp http;
    Admin admin;

    @BeforeEach
    void preparar() {
        http = new ClienteHttp(porta, json);
        admin = novoAdministrador(http);
    }

    @Test
    void regraVioladaComExtras() {
        var r = http.comToken(admin.token()).comContexto(admin.academiaId(), "ADMIN").get("/api/v1/teste-regra-com-extras");
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.texto("codigo")).isEqualTo("X");
        assertThat(r.corpo().get("aulas").get(0).asString()).isEqualTo("a");
    }

    @Test
    void semHeaders400() {
        var r = http.comToken(admin.token()).get("/api/v1/teste-contexto");
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.texto("codigo")).isEqualTo("CONTEXTO_AUSENTE");
    }

    @Test
    void headerMalFormado400() {
        var r = http.comToken(admin.token()).comContexto("nao-e-uuid", "ADMIN").get("/api/v1/teste-contexto");
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.texto("codigo")).isEqualTo("CONTEXTO_AUSENTE");
    }

    @Test
    void academiaSemVinculo403() {
        UUID outra = DadosDeTeste.academia(su.jdbc());
        var r = http.comToken(admin.token()).comContexto(outra, "ADMIN").get("/api/v1/teste-contexto");
        assertThat(r.status()).isEqualTo(403);
        assertThat(r.texto("codigo")).isEqualTo("SEM_VINCULO");
    }

    @Test
    void perfilErrado403() {
        var r = http.comToken(admin.token()).comContexto(admin.academiaId(), "ALUNO").get("/api/v1/teste-contexto");
        assertThat(r.status()).isEqualTo(403);
        assertThat(r.texto("codigo")).isEqualTo("SEM_VINCULO");
    }

    @Test
    void contextoDefineSetConfigNaTransacao() {
        var r = http.comToken(admin.token()).comContexto(admin.academiaId(), "ADMIN").get("/api/v1/teste-contexto");
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.texto("academiaId")).isEqualTo(admin.academiaId().toString());
    }

    @Test
    void academiaInativaRecusaEscrita() {
        su.jdbc().update("update academia set status = 'INATIVA' where id = ?", admin.academiaId());
        ClienteHttp ctx = http.comToken(admin.token()).comContexto(admin.academiaId(), "ADMIN");

        assertThat(ctx.get("/api/v1/teste-contexto").status()).isEqualTo(200);
        var escrita = ctx.post("/api/v1/teste-contexto", Map.of());
        assertThat(escrita.status()).isEqualTo(409);
        assertThat(escrita.texto("codigo")).isEqualTo("ACADEMIA_INATIVA");
    }

    @Test
    void alunoCanceladoSemAcesso() {
        String email = emailUnico("cancelado");
        UUID usuario = DadosDeTeste.usuario(su.jdbc(), email);
        DadosDeTeste.aluno(su.jdbc(), usuario, admin.academiaId(), "CANCELADO");
        String token = http.post("/api/v1/auth/login", Map.of("email", email, "senha", SENHA)).texto("accessToken");

        var r = http.comToken(token).comContexto(admin.academiaId(), "ALUNO").get("/api/v1/eu/aluno");

        assertThat(r.status()).isEqualTo(403);
        assertThat(r.texto("codigo")).isEqualTo("SEM_VINCULO");
    }

    @Test
    void rotasSemTenantNaoExigemContexto() {
        assertThat(http.comToken(admin.token()).get("/api/v1/eu/vinculos").status()).isEqualTo(200);
    }
}
