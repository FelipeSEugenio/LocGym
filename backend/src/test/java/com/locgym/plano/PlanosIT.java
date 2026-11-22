package com.locgym.plano;

import static com.locgym.apoio.Cenarios.SENHA;
import static com.locgym.apoio.Cenarios.comoAdmin;
import static com.locgym.apoio.Cenarios.emailUnico;
import static com.locgym.apoio.Cenarios.novoAdministrador;
import static org.assertj.core.api.Assertions.assertThat;

import com.locgym.TestcontainersConfig;
import com.locgym.TestcontainersConfig.BancoSuperusuario;
import com.locgym.apoio.Cenarios.Admin;
import com.locgym.apoio.ClienteHttp;
import com.locgym.compartilhado.tenancy.DadosDeTeste;
import java.util.HashMap;
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
class PlanosIT {

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

    static Map<String, Object> plano(String nome, String valor, String periodicidade) {
        Map<String, Object> p = new HashMap<>();
        p.put("nome", nome);
        p.put("descricao", "Treinos ilimitados");
        p.put("valor", new java.math.BigDecimal(valor));
        p.put("periodicidade", periodicidade);
        return p;
    }

    @Test
    void crudCompleto() {
        var criado = ctx.post("/api/v1/planos", plano("Mensal", "150.00", "MENSAL"));
        assertThat(criado.status()).isEqualTo(201);
        assertThat(criado.texto("status")).isEqualTo("ATIVO");
        String id = criado.texto("id");

        assertThat(ctx.get("/api/v1/planos/" + id).texto("nome")).isEqualTo("Mensal");

        var alterado = ctx.put("/api/v1/planos/" + id, plano("Mensal Plus", "180.50", "MENSAL"));
        assertThat(alterado.status()).isEqualTo(200);
        assertThat(alterado.corpo().get("valor").decimalValue()).isEqualByComparingTo("180.50");

        var lista = ctx.get("/api/v1/planos");
        assertThat(lista.corpo().size()).isEqualTo(1);
        assertThat(lista.corpo().get(0).get("nome").asString()).isEqualTo("Mensal Plus");
    }

    @Test
    void valorInvalido400() {
        var r = ctx.post("/api/v1/planos", plano("Grátis", "0", "MENSAL"));
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.corpo().get("erros").get(0).get("campo").asString()).isEqualTo("valor");
    }

    @Test
    void nomeDuplicadoNaMesmaAcademia409() {
        ctx.post("/api/v1/planos", plano("Anual", "1500.00", "ANUAL"));
        var r = ctx.post("/api/v1/planos", plano("Anual", "1400.00", "ANUAL"));
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.texto("codigo")).isEqualTo("PLANO_JA_EXISTE");
    }

    @Test
    void mesmoNomeEmOutraAcademiaPermitido() {
        ctx.post("/api/v1/planos", plano("Trimestral", "400.00", "TRIMESTRAL"));
        ClienteHttp outra = comoAdmin(http, novoAdministrador(http));
        assertThat(outra.post("/api/v1/planos", plano("Trimestral", "420.00", "TRIMESTRAL")).status()).isEqualTo(201);
    }

    @Test
    void filtroPorStatus() {
        ctx.post("/api/v1/planos", plano("A", "100.00", "MENSAL"));
        String inativo = ctx.post("/api/v1/planos", plano("B", "100.00", "MENSAL")).texto("id");
        assertThat(ctx.patch("/api/v1/planos/" + inativo + "/status", Map.of("status", "INATIVO")).status()).isEqualTo(200);

        assertThat(ctx.get("/api/v1/planos?status=ATIVO").corpo().size()).isEqualTo(1);
        assertThat(ctx.get("/api/v1/planos?status=INATIVO").corpo().get(0).get("nome").asString()).isEqualTo("B");
        assertThat(ctx.get("/api/v1/planos").corpo().size()).isEqualTo(2);
    }

    @Test
    void semExclusao() {
        String id = ctx.post("/api/v1/planos", plano("Fixo", "100.00", "MENSAL")).texto("id");
        assertThat(ctx.delete("/api/v1/planos/" + id).status()).isEqualTo(405);
    }

    @Test
    void academiaIdVemDoContexto() {
        Admin outro = novoAdministrador(http);
        var corpo = plano("Intruso", "100.00", "MENSAL");
        corpo.put("academiaId", outro.academiaId().toString());

        String id = ctx.post("/api/v1/planos", corpo).texto("id");

        assertThat(su.jdbc().queryForObject("select academia_id from plano where id = ?::uuid", UUID.class, id))
                .isEqualTo(admin.academiaId());
    }

    @Test
    void isolamento() {
        ClienteHttp outra = comoAdmin(http, novoAdministrador(http));
        String idDaOutra = outra.post("/api/v1/planos", plano("Secreto", "100.00", "MENSAL")).texto("id");

        assertThat(ctx.get("/api/v1/planos/" + idDaOutra).status()).isEqualTo(404);
        assertThat(ctx.put("/api/v1/planos/" + idDaOutra, plano("Hackeado", "1.00", "MENSAL")).status()).isEqualTo(404);
        assertThat(ctx.patch("/api/v1/planos/" + idDaOutra + "/status", Map.of("status", "INATIVO")).status()).isEqualTo(404);
        assertThat(ctx.get("/api/v1/planos").corpo().size()).isZero();
        assertThat(outra.get("/api/v1/planos/" + idDaOutra).texto("nome")).isEqualTo("Secreto");
    }

    @Test
    void alunoNaoAcessa() {
        String email = emailUnico("aluno");
        UUID usuario = DadosDeTeste.usuario(su.jdbc(), email);
        DadosDeTeste.aluno(su.jdbc(), usuario, admin.academiaId(), "ATIVO");
        String token = http.post("/api/v1/auth/login", Map.of("email", email, "senha", SENHA)).texto("accessToken");

        assertThat(http.comToken(token).comContexto(admin.academiaId(), "ALUNO").get("/api/v1/planos").status())
                .isEqualTo(403);
    }

    @Test
    void statusAuditado() {
        String id = ctx.post("/api/v1/planos", plano("Auditado", "100.00", "MENSAL")).texto("id");
        ctx.patch("/api/v1/planos/" + id + "/status", Map.of("status", "INATIVO"));

        assertThat(su.jdbc().queryForObject(
                "select depois->>'status' from auditoria where entidade_id = ?::uuid and acao = 'PLANO_STATUS_ALTERADO'",
                String.class, id)).isEqualTo("INATIVO");
    }
}
