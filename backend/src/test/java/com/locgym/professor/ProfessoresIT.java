package com.locgym.professor;

import static com.locgym.apoio.Cenarios.comoAdmin;
import static com.locgym.apoio.Cenarios.novoAdministrador;
import static org.assertj.core.api.Assertions.assertThat;

import com.locgym.TestcontainersConfig;
import com.locgym.TestcontainersConfig.BancoSuperusuario;
import com.locgym.apoio.ClienteHttp;
import java.util.HashMap;
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
class ProfessoresIT {

    @LocalServerPort int porta;
    @Autowired JsonMapper json;
    @Autowired BancoSuperusuario su;
    ClienteHttp http;
    ClienteHttp ctx;

    @BeforeEach
    void preparar() {
        http = new ClienteHttp(porta, json);
        ctx = comoAdmin(http, novoAdministrador(http));
    }

    static Map<String, Object> professor(String nome, String email) {
        Map<String, Object> p = new HashMap<>();
        p.put("nome", nome);
        p.put("email", email);
        p.put("telefone", "11977776666");
        p.put("dataNascimento", "1985-03-20");
        p.put("especialidade", "Faixa preta 3º grau");
        return p;
    }

    @Test
    void crudCompleto() {
        var criado = ctx.post("/api/v1/professores", professor("Carlos Gracie", "carlos@academia.com"));
        assertThat(criado.status()).isEqualTo(201);
        assertThat(criado.texto("status")).isEqualTo("ATIVO");
        String id = criado.texto("id");

        var alterado = ctx.put("/api/v1/professores/" + id, professor("Carlos G.", "carlos@academia.com"));
        assertThat(alterado.status()).isEqualTo(200);
        assertThat(ctx.get("/api/v1/professores/" + id).texto("nome")).isEqualTo("Carlos G.");
        assertThat(ctx.get("/api/v1/professores/" + id).texto("dataNascimento")).isEqualTo("1985-03-20");
    }

    @Test
    void nomeObrigatorio400() {
        assertThat(ctx.post("/api/v1/professores", professor(" ", null)).status()).isEqualTo(400);
    }

    @Test
    void buscaPorNomeEEmail() {
        ctx.post("/api/v1/professores", professor("Helio Faria", "helio@x.com"));
        ctx.post("/api/v1/professores", professor("Rickson Lima", "rick@x.com"));

        assertThat(ctx.get("/api/v1/professores?busca=hELIO").corpo().get("itens").size()).isEqualTo(1);
        assertThat(ctx.get("/api/v1/professores?busca=rick@").corpo().get("itens").get(0).get("nome").asString())
                .isEqualTo("Rickson Lima");
    }

    @Test
    void paginacao() {
        for (int i = 1; i <= 25; i++) {
            ctx.post("/api/v1/professores", professor(String.format("Professor %02d", i), null));
        }

        var primeira = ctx.get("/api/v1/professores?pagina=0&tamanho=10").corpo();
        var ultima = ctx.get("/api/v1/professores?pagina=2&tamanho=10").corpo();

        assertThat(primeira.get("total").asLong()).isEqualTo(25);
        assertThat(primeira.get("itens").size()).isEqualTo(10);
        assertThat(primeira.get("itens").get(0).get("nome").asString()).isEqualTo("Professor 01");
        assertThat(ultima.get("itens").size()).isEqualTo(5);
        assertThat(ultima.get("pagina").asInt()).isEqualTo(2);
    }

    @Test
    void isolamento() {
        ClienteHttp outra = comoAdmin(http, novoAdministrador(http));
        String id = outra.post("/api/v1/professores", professor("Oculto", null)).texto("id");

        assertThat(ctx.get("/api/v1/professores/" + id).status()).isEqualTo(404);
        assertThat(ctx.put("/api/v1/professores/" + id, professor("X", null)).status()).isEqualTo(404);
        assertThat(ctx.get("/api/v1/professores?busca=Oculto").corpo().get("total").asLong()).isZero();
    }

    @Test
    void statusAuditado() {
        String id = ctx.post("/api/v1/professores", professor("Auditado", null)).texto("id");
        assertThat(ctx.patch("/api/v1/professores/" + id + "/status", Map.of("status", "INATIVO")).texto("status"))
                .isEqualTo("INATIVO");

        assertThat(su.jdbc().queryForObject(
                "select count(*) from auditoria where entidade_id = ?::uuid and acao = 'PROFESSOR_STATUS_ALTERADO'",
                Integer.class, id)).isEqualTo(1);
    }
}
