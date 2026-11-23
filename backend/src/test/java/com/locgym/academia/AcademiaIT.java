package com.locgym.academia;

import static com.locgym.apoio.Cenarios.SENHA;
import static com.locgym.apoio.Cenarios.comoAdmin;
import static com.locgym.apoio.Cenarios.dadosAcademia;
import static com.locgym.apoio.Cenarios.emailUnico;
import static com.locgym.apoio.Cenarios.novoAdministrador;
import static org.assertj.core.api.Assertions.assertThat;

import com.locgym.TestcontainersConfig;
import com.locgym.TestcontainersConfig.BancoSuperusuario;
import com.locgym.apoio.Cenarios.Admin;
import com.locgym.apoio.ClienteHttp;
import com.locgym.compartilhado.tenancy.DadosDeTeste;
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
class AcademiaIT {

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
    void cadastroAuditaCriacaoDaAcademia() {
        assertThat(su.jdbc().queryForObject(
                "select count(*) from auditoria where academia_id = ? and acao = 'ACADEMIA_CRIADA' and usuario_id = ?",
                Integer.class, admin.academiaId(), admin.usuarioId())).isEqualTo(1);
    }

    @Test
    void criarNovaAcademiaVinculaCriador() {
        var r = http.comToken(admin.token()).post("/api/v1/academias", dadosAcademia("Filial Centro"));

        assertThat(r.status()).isEqualTo(201);
        assertThat(r.texto("nome")).isEqualTo("Filial Centro");
        assertThat(r.texto("status")).isEqualTo("ATIVA");
        var vinculos = http.comToken(admin.token()).get("/api/v1/eu/vinculos").corpo();
        assertThat(vinculos.size()).isEqualTo(2);
        assertThat(su.jdbc().queryForObject("select count(*) from auditoria where academia_id = ? and acao = 'ACADEMIA_CRIADA'",
                Integer.class, UUID.fromString(r.texto("id")))).isEqualTo(1);
    }

    @Test
    void alunoTambemPodeCriarAcademia() {
        String email = emailUnico("aluno-dono");
        UUID usuario = DadosDeTeste.usuario(su.jdbc(), email);
        DadosDeTeste.aluno(su.jdbc(), usuario, admin.academiaId(), "ATIVO");
        String token = http.post("/api/v1/auth/login", Map.of("email", email, "senha", SENHA)).texto("accessToken");

        var r = http.comToken(token).post("/api/v1/academias", dadosAcademia("Academia do Aluno"));

        assertThat(r.status()).isEqualTo(201);
        var perfis = http.comToken(token).get("/api/v1/eu/vinculos").corpo();
        assertThat(perfis.size()).isEqualTo(2);
    }

    @Test
    void cnpjDuplicado409() {
        var dados = dadosAcademia("Primeira");
        assertThat(http.comToken(admin.token()).post("/api/v1/academias", dados).status()).isEqualTo(201);
        dados.put("nome", "Segunda");

        var r = http.comToken(admin.token()).post("/api/v1/academias", dados);

        assertThat(r.status()).isEqualTo(409);
        assertThat(r.texto("codigo")).isEqualTo("CNPJ_JA_CADASTRADO");
    }

    @Test
    void editarDadosDaAcademiaAtiva() {
        ClienteHttp ctx = comoAdmin(http, admin);
        var dados = dadosAcademia("Nome Novo");
        dados.put("cnpj", ctx.get("/api/v1/academia").texto("cnpj"));

        var r = ctx.put("/api/v1/academia", dados);

        assertThat(r.status()).isEqualTo(200);
        assertThat(ctx.get("/api/v1/academia").texto("nome")).isEqualTo("Nome Novo");
    }

    @Test
    void inativarEReativar() {
        ClienteHttp ctx = comoAdmin(http, admin);

        assertThat(ctx.patch("/api/v1/academia/status", Map.of("status", "INATIVA")).status()).isEqualTo(200);
        var escrita = ctx.put("/api/v1/academia", dadosAcademia("Bloqueada"));
        assertThat(escrita.status()).isEqualTo(409);
        assertThat(escrita.texto("codigo")).isEqualTo("ACADEMIA_INATIVA");
        assertThat(ctx.get("/api/v1/academia").texto("status")).isEqualTo("INATIVA");

        assertThat(ctx.patch("/api/v1/academia/status", Map.of("status", "ATIVA")).status()).isEqualTo(200);
        assertThat(ctx.get("/api/v1/academia").texto("status")).isEqualTo("ATIVA");
        assertThat(su.jdbc().queryForObject(
                "select count(*) from auditoria where academia_id = ? and acao = 'ACADEMIA_STATUS_ALTERADO'",
                Integer.class, admin.academiaId())).isEqualTo(2);
    }

    @Test
    void isolamento() {
        Admin outro = novoAdministrador(http);
        var r = http.comToken(admin.token()).comContexto(outro.academiaId(), "ADMIN").get("/api/v1/academia");
        assertThat(r.status()).isEqualTo(403);
    }

    @Test
    void parametrosDeCobrancaPadraoEEditaveis() {
        var cliente = http.comToken(admin.token()).comContexto(admin.academiaId(), "ADMIN");
        var atual = cliente.get("/api/v1/academia");
        assertThat(atual.corpo().get("diasAntecedenciaCobranca").asInt()).isEqualTo(7);
        assertThat(atual.corpo().get("diasCarencia").asInt()).isEqualTo(10);

        var dados = new java.util.LinkedHashMap<String, Object>(dadosAcademia(atual.texto("nome")));
        dados.put("cnpj", atual.texto("cnpj"));
        dados.put("diasAntecedenciaCobranca", 3);
        dados.put("diasCarencia", 0);
        var r = cliente.put("/api/v1/academia", dados);

        assertThat(r.status()).as(r.corpo().toString()).isEqualTo(200);
        var depois = cliente.get("/api/v1/academia");
        assertThat(depois.corpo().get("diasAntecedenciaCobranca").asInt()).isEqualTo(3);
        assertThat(depois.corpo().get("diasCarencia").asInt()).isEqualTo(0);
        assertThat(su.jdbc().queryForObject(
                "select count(*) from auditoria where academia_id = ? and acao = 'PARAMETROS_COBRANCA_ALTERADOS'",
                Integer.class, admin.academiaId())).isEqualTo(1);

        dados.put("diasAntecedenciaCobranca", 0);
        assertThat(cliente.put("/api/v1/academia", dados).status()).isEqualTo(400);
        dados.put("diasAntecedenciaCobranca", 7);
        dados.put("diasCarencia", 61);
        assertThat(cliente.put("/api/v1/academia", dados).status()).isEqualTo(400);
    }

    @Test
    void semParametrosMantemValores() {
        var cliente = http.comToken(admin.token()).comContexto(admin.academiaId(), "ADMIN");
        var dados = new java.util.LinkedHashMap<String, Object>(dadosAcademia("Outro nome"));
        dados.put("cnpj", cliente.get("/api/v1/academia").texto("cnpj"));

        assertThat(cliente.put("/api/v1/academia", dados).status()).isEqualTo(200);

        var depois = cliente.get("/api/v1/academia");
        assertThat(depois.corpo().get("diasAntecedenciaCobranca").asInt()).isEqualTo(7);
        assertThat(depois.corpo().get("diasCarencia").asInt()).isEqualTo(10);
    }

    @Test
    void perfilSistemaNaoPodeSerUsadoNaRequisicao() {
        var r = http.comToken(admin.token()).comContexto(admin.academiaId(), "SISTEMA").get("/api/v1/academia");

        assertThat(r.status()).isEqualTo(400);
        assertThat(r.texto("codigo")).isEqualTo("CONTEXTO_AUSENTE");
    }
}
