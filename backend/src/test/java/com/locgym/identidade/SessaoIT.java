package com.locgym.identidade;

import static com.locgym.apoio.Cenarios.SENHA;
import static com.locgym.apoio.Cenarios.cadastroAdministrador;
import static com.locgym.apoio.Cenarios.emailUnico;
import static org.assertj.core.api.Assertions.assertThat;

import com.locgym.TestcontainersConfig;
import com.locgym.TestcontainersConfig.BancoSuperusuario;
import com.locgym.apoio.ClienteHttp;
import com.locgym.apoio.RelogioDeTeste;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "locgym.outbox.relay-habilitado=false")
@Import({TestcontainersConfig.class, RelogioDeTeste.Config.class})
class SessaoIT {

    @LocalServerPort int porta;
    @Autowired JsonMapper json;
    @Autowired BancoSuperusuario su;
    @Autowired RelogioDeTeste relogio;
    ClienteHttp http;

    @BeforeEach
    void preparar() {
        http = new ClienteHttp(porta, json);
    }

    private String loginEObterCookie(String email) {
        var r = http.post("/api/v1/auth/login", Map.of("email", email, "senha", SENHA));
        assertThat(r.status()).isEqualTo(200);
        return valorDoCookie(r.headers());
    }

    private static String setCookie(HttpHeaders headers) {
        return headers.getFirst(HttpHeaders.SET_COOKIE);
    }

    private static String valorDoCookie(HttpHeaders headers) {
        String c = setCookie(headers);
        assertThat(c).startsWith("locgym_refresh=");
        return c.substring("locgym_refresh=".length(), c.indexOf(';'));
    }

    private ClienteHttp comCookie(String valor) {
        return http.comHeader("Cookie", "locgym_refresh=" + valor);
    }

    private String novoAdmin() {
        String email = emailUnico("sessao");
        assertThat(http.post("/api/v1/auth/cadastro-administrador", cadastroAdministrador(email, SENHA)).status())
                .isEqualTo(201);
        return email;
    }

    private static String sha256(String valor) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void loginEmiteCookieSeguro() {
        var r = http.post("/api/v1/auth/login", Map.of("email", novoAdmin(), "senha", SENHA));

        String cookie = setCookie(r.headers());
        assertThat(cookie).startsWith("locgym_refresh=").contains("HttpOnly").contains("Secure")
                .contains("SameSite=Strict").contains("Path=/api/v1/auth");
    }

    @Test
    void cadastroTambemEmiteCookie() {
        var r = http.post("/api/v1/auth/cadastro-administrador", cadastroAdministrador(emailUnico("c"), SENHA));
        assertThat(setCookie(r.headers())).startsWith("locgym_refresh=");
    }

    @Test
    void refreshRotacionaEGuardaSoOHash() throws Exception {
        String original = loginEObterCookie(novoAdmin());

        var r = comCookie(original).post("/api/v1/auth/refresh", Map.of());

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.texto("accessToken")).isNotBlank();
        String novo = valorDoCookie(r.headers());
        assertThat(novo).isNotEqualTo(original);
        assertThat(su.jdbc().queryForObject("select count(*) from refresh_token where token_hash = ?",
                Integer.class, original)).isZero();
        assertThat(su.jdbc().queryForObject("select count(*) from refresh_token where token_hash = ?",
                Integer.class, sha256(original))).isEqualTo(1);
    }

    @Test
    void refreshSemCookie401() {
        assertThat(http.post("/api/v1/auth/refresh", Map.of()).status()).isEqualTo(401);
    }

    @Test
    void reusoDentroDaJanelaNaoRevoga() {
        String original = loginEObterCookie(novoAdmin());

        var primeira = comCookie(original).post("/api/v1/auth/refresh", Map.of());
        var segunda = comCookie(original).post("/api/v1/auth/refresh", Map.of());

        assertThat(primeira.status()).isEqualTo(200);
        assertThat(segunda.status()).isEqualTo(200);
        assertThat(comCookie(valorDoCookie(primeira.headers())).post("/api/v1/auth/refresh", Map.of()).status())
                .isEqualTo(200);
    }

    @Test
    void reusoForaDaJanelaRevogaFamilia() {
        String original = loginEObterCookie(novoAdmin());
        var primeira = comCookie(original).post("/api/v1/auth/refresh", Map.of());
        String maisNovo = valorDoCookie(primeira.headers());

        relogio.avancar(Duration.ofSeconds(11));

        assertThat(comCookie(original).post("/api/v1/auth/refresh", Map.of()).status()).isEqualTo(401);
        assertThat(comCookie(maisNovo).post("/api/v1/auth/refresh", Map.of()).status()).isEqualTo(401);
    }

    @Test
    void logoutRevogaEApagaCookie() {
        String cookie = loginEObterCookie(novoAdmin());

        var r = comCookie(cookie).post("/api/v1/auth/logout", Map.of());

        assertThat(r.status()).isEqualTo(204);
        assertThat(setCookie(r.headers())).startsWith("locgym_refresh=").contains("Max-Age=0");
        assertThat(comCookie(cookie).post("/api/v1/auth/refresh", Map.of()).status()).isEqualTo(401);
    }

    @Test
    void trocaObrigatoriaBloqueia() {
        String email = emailUnico("troca");
        su.jdbc().update("insert into usuario (id, email, nome, senha_hash, deve_trocar_senha) values (?, ?, 'Novo', ?, true)",
                UUID.randomUUID(), email, com.locgym.compartilhado.tenancy.DadosDeTeste.HASH_SENHA_PADRAO);
        var login = http.post("/api/v1/auth/login", Map.of("email", email, "senha", SENHA));
        assertThat(login.corpo().get("usuario").get("deveTrocarSenha").asBoolean()).isTrue();
        ClienteHttp logado = http.comToken(login.texto("accessToken"));

        var bloqueado = logado.get("/api/v1/eu/vinculos");
        assertThat(bloqueado.status()).isEqualTo(403);
        assertThat(bloqueado.texto("codigo")).isEqualTo("TROCA_DE_SENHA_OBRIGATORIA");

        var troca = logado.post("/api/v1/auth/trocar-senha",
                Map.of("senhaAtual", SENHA, "novaSenha", "nova-senha-456"));
        assertThat(troca.status()).isEqualTo(200);
        assertThat(troca.corpo().get("usuario").get("deveTrocarSenha").asBoolean()).isFalse();

        assertThat(http.comToken(troca.texto("accessToken")).get("/api/v1/eu/vinculos").status()).isEqualTo(200);
        assertThat(http.post("/api/v1/auth/login", Map.of("email", email, "senha", "nova-senha-456")).status())
                .isEqualTo(200);
    }

    @Test
    void trocaComSenhaAtualErrada401() {
        var login = http.post("/api/v1/auth/login", Map.of("email", novoAdmin(), "senha", SENHA));

        var r = http.comToken(login.texto("accessToken")).post("/api/v1/auth/trocar-senha",
                Map.of("senhaAtual", "errada-123", "novaSenha", "nova-senha-456"));

        assertThat(r.status()).isEqualTo(401);
    }
}
