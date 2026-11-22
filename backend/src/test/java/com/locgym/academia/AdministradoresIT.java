package com.locgym.academia;

import static com.locgym.apoio.Cenarios.SENHA;
import static com.locgym.apoio.Cenarios.comoAdmin;
import static com.locgym.apoio.Cenarios.emailUnico;
import static com.locgym.apoio.Cenarios.novoAdministrador;
import static org.assertj.core.api.Assertions.assertThat;

import com.locgym.TestcontainersConfig;
import com.locgym.TestcontainersConfig.BancoSuperusuario;
import com.locgym.apoio.Cenarios.Admin;
import com.locgym.apoio.ClienteHttp;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "locgym.outbox.relay-habilitado=false")
@Import(TestcontainersConfig.class)
class AdministradoresIT {

    @LocalServerPort int porta;
    @Autowired JsonMapper json;
    @Autowired BancoSuperusuario su;
    ClienteHttp http;
    Admin admin;
    ClienteHttp ctx;

    @BeforeEach
    void preparar() {
        http = new ClienteHttp(porta, json);
        admin = novoAdministrador(http);
        ctx = comoAdmin(http, admin);
    }

    private static Map<String, Object> convite(String nome, String email, String senha) {
        return Map.of("nome", nome, "email", email, "senhaInicial", senha);
    }

    private JsonNode administradorNaLista(String email) {
        var lista = ctx.get("/api/v1/administradores").corpo();
        return StreamSupport.stream(lista.spliterator(), false)
                .filter(a -> a.get("email").asString().equals(email)).findFirst().orElse(null);
    }

    @Test
    void listaComecaComOCriador() {
        var r = ctx.get("/api/v1/administradores");
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.corpo().size()).isEqualTo(1);
        assertThat(r.corpo().get(0).get("email").asString()).isEqualTo(admin.email());
        assertThat(r.corpo().get(0).get("nome").asString()).isEqualTo("Dono da Academia");
    }

    @Test
    void conviteParaEmailNovoEExistenteRespondemIgual() {
        Admin existente = novoAdministrador(http);

        var paraExistente = ctx.post("/api/v1/administradores", convite("Professor Zé", existente.email(), "qualquer-999"));
        var paraNovo = ctx.post("/api/v1/administradores", convite("Recepção", emailUnico("novo"), "inicial-123"));

        assertThat(paraExistente.status()).isEqualTo(201);
        assertThat(paraNovo.status()).isEqualTo(201);
        assertThat(paraExistente.corpo().propertyNames()).containsExactlyInAnyOrderElementsOf(paraNovo.corpo().propertyNames());
        assertThat(paraExistente.texto("nome")).isEqualTo("Professor Zé");
        assertThat(administradorNaLista(existente.email()).get("nome").asString()).isEqualTo("Professor Zé");
        assertThat(http.post("/api/v1/auth/login", Map.of("email", existente.email(), "senha", SENHA)).status())
                .as("senha antiga continua valendo").isEqualTo(200);
        assertThat(http.post("/api/v1/auth/login", Map.of("email", existente.email(), "senha", "qualquer-999")).status())
                .isEqualTo(401);
    }

    @Test
    void convidadoNovoDeveTrocarSenha() {
        String email = emailUnico("convidado");
        ctx.post("/api/v1/administradores", convite("Convidado", email, "inicial-123"));

        var login = http.post("/api/v1/auth/login", Map.of("email", email, "senha", "inicial-123"));

        assertThat(login.status()).isEqualTo(200);
        assertThat(login.corpo().get("usuario").get("deveTrocarSenha").asBoolean()).isTrue();
        assertThat(login.corpo().get("vinculos").get(0).get("academiaId").asString()).isEqualTo(admin.academiaId().toString());
    }

    @Test
    void conviteDeQuemJaEAdministrador409() {
        var r = ctx.post("/api/v1/administradores", convite("De novo", admin.email(), "qualquer-999"));
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.texto("codigo")).isEqualTo("JA_E_ADMINISTRADOR");
    }

    @Test
    void conviteComSenhaCurta400() {
        assertThat(ctx.post("/api/v1/administradores", convite("X", emailUnico("x"), "1234567")).status()).isEqualTo(400);
    }

    @Test
    void naoRemoveUltimoAdministrador() {
        var r = ctx.delete("/api/v1/administradores/" + admin.usuarioId());
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.texto("codigo")).isEqualTo("ULTIMO_ADMINISTRADOR");
    }

    @Test
    void adminPodeSairSeNaoForOUltimo() {
        ctx.post("/api/v1/administradores", convite("Outro", emailUnico("outro"), "inicial-123"));

        assertThat(ctx.delete("/api/v1/administradores/" + admin.usuarioId()).status()).isEqualTo(204);
        assertThat(ctx.get("/api/v1/administradores").status()).isEqualTo(403);
    }

    @Test
    void adminRemovidoPerdeAcessoNaHora() {
        Admin b = novoAdministrador(http);
        ctx.post("/api/v1/administradores", convite("B", b.email(), "ignorada-123"));
        ClienteHttp bNaAcademiaA = http.comToken(b.token()).comContexto(admin.academiaId(), "ADMIN");
        assertThat(bNaAcademiaA.get("/api/v1/administradores").status()).isEqualTo(200);

        assertThat(ctx.delete("/api/v1/administradores/" + b.usuarioId()).status()).isEqualTo(204);

        var depois = bNaAcademiaA.get("/api/v1/administradores");
        assertThat(depois.status()).isEqualTo(403);
        assertThat(depois.texto("codigo")).isEqualTo("SEM_VINCULO");
    }

    @Test
    void removerQuemNaoEAdministrador404() {
        Admin outro = novoAdministrador(http);
        assertThat(ctx.delete("/api/v1/administradores/" + outro.usuarioId()).status()).isEqualTo(404);
    }

    @Test
    void auditoriaSemSenha() {
        String email = emailUnico("auditado");
        var convidado = ctx.post("/api/v1/administradores", convite("Auditado", email, "segredo-12345"));
        ctx.delete("/api/v1/administradores/" + convidado.texto("usuarioId"));

        var acoes = su.jdbc().queryForList(
                "select acao, coalesce(antes::text, '') || coalesce(depois::text, '') as dados from auditoria "
                        + "where academia_id = ? and acao like 'ADMINISTRADOR_%'", admin.academiaId());
        assertThat(acoes).extracting(l -> l.get("acao"))
                .containsExactlyInAnyOrder("ADMINISTRADOR_ADICIONADO", "ADMINISTRADOR_REMOVIDO");
        assertThat(acoes).allSatisfy(l -> assertThat((String) l.get("dados")).doesNotContain("segredo").doesNotContainIgnoringCase("senha"));
    }
}
