package com.locgym.identidade;

import static com.locgym.apoio.Cenarios.SENHA;
import static com.locgym.apoio.Cenarios.cadastroAdministrador;
import static com.locgym.apoio.Cenarios.emailUnico;
import static org.assertj.core.api.Assertions.assertThat;

import com.locgym.TestcontainersConfig;
import com.locgym.apoio.ClienteHttp;
import java.util.Map;
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
class LoginIT {

    @LocalServerPort int porta;
    @Autowired JsonMapper json;
    ClienteHttp http;

    @BeforeEach
    void preparar() {
        http = new ClienteHttp(porta, json);
    }

    @Test
    void cadastroDeAdministradorCriaAcademiaEVinculo() {
        String email = emailUnico("dono");
        var r = http.post("/api/v1/auth/cadastro-administrador", cadastroAdministrador(email, SENHA));

        assertThat(r.status()).isEqualTo(201);
        assertThat(r.texto("accessToken")).isNotBlank();
        assertThat(r.corpo().get("usuario").get("email").asString()).isEqualTo(email);
        assertThat(r.corpo().get("usuario").get("deveTrocarSenha").asBoolean()).isFalse();
        var vinculos = r.corpo().get("vinculos");
        assertThat(vinculos.size()).isEqualTo(1);
        assertThat(vinculos.get(0).get("perfil").asString()).isEqualTo("ADMIN");
        assertThat(vinculos.get(0).get("academiaStatus").asString()).isEqualTo("ATIVA");
        assertThat(vinculos.get(0).get("academiaNome").asString()).startsWith("Academia ");
    }

    @Test
    void cadastroComEmailExistente409() {
        String email = emailUnico("repetido");
        assertThat(http.post("/api/v1/auth/cadastro-administrador", cadastroAdministrador(email, SENHA)).status())
                .isEqualTo(201);

        var r = http.post("/api/v1/auth/cadastro-administrador", cadastroAdministrador(email, SENHA));

        assertThat(r.status()).isEqualTo(409);
        assertThat(r.texto("codigo")).isEqualTo("EMAIL_JA_CADASTRADO");
    }

    @Test
    void loginOk() {
        String email = emailUnico("login");
        http.post("/api/v1/auth/cadastro-administrador", cadastroAdministrador(email, SENHA));

        var r = http.post("/api/v1/auth/login", Map.of("email", email, "senha", SENHA));

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.texto("accessToken")).isNotBlank();
        assertThat(r.texto("expiraEm")).isNotBlank();
        assertThat(r.corpo().get("vinculos").get(0).get("perfil").asString()).isEqualTo("ADMIN");
    }

    @Test
    void senhaErradaEEmailInexistenteRespondemIgual() {
        String email = emailUnico("igual");
        http.post("/api/v1/auth/cadastro-administrador", cadastroAdministrador(email, SENHA));

        var senhaErrada = http.post("/api/v1/auth/login", Map.of("email", email, "senha", "outra-senha-1"));
        var inexistente = http.post("/api/v1/auth/login", Map.of("email", emailUnico("nada"), "senha", SENHA));

        assertThat(senhaErrada.status()).isEqualTo(401);
        assertThat(inexistente.status()).isEqualTo(401);
        assertThat(senhaErrada.texto("detail")).isEqualTo("E-mail ou senha inválidos");
        assertThat(inexistente.corpo()).isEqualTo(senhaErrada.corpo());
    }

    @Test
    void emailENormalizado() {
        String base = emailUnico("Ana");
        http.post("/api/v1/auth/cadastro-administrador", cadastroAdministrador("  " + base.toUpperCase() + " ", SENHA));

        var r = http.post("/api/v1/auth/login", Map.of("email", base.toLowerCase(), "senha", SENHA));

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.corpo().get("usuario").get("email").asString()).isEqualTo(base.toLowerCase());
    }

    @Test
    void senhaCurtaRecusada() {
        var r = http.post("/api/v1/auth/cadastro-administrador", cadastroAdministrador(emailUnico("curta"), "1234567"));

        assertThat(r.status()).isEqualTo(400);
        assertThat(r.corpo().get("erros").get(0).get("campo").asString()).isEqualTo("senha");
    }

    @Test
    void rotaProtegidaSemToken401() {
        assertThat(http.get("/api/v1/eu/vinculos").status()).isEqualTo(401);
    }

    @Test
    void euVinculosComToken200() {
        String email = emailUnico("eu");
        var cadastro = http.post("/api/v1/auth/cadastro-administrador", cadastroAdministrador(email, SENHA));

        var r = http.comToken(cadastro.texto("accessToken")).get("/api/v1/eu/vinculos");

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.corpo().size()).isEqualTo(1);
        assertThat(r.corpo().get(0).get("perfil").asString()).isEqualTo("ADMIN");
    }

    @Test
    void tokenInvalido401() {
        assertThat(http.comToken("nao-e-um-jwt").get("/api/v1/eu/vinculos").status()).isEqualTo(401);
    }
}
