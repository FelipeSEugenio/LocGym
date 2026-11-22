package com.locgym.aluno;

import static com.locgym.apoio.Cenarios.SENHA;
import static com.locgym.apoio.Cenarios.comoAdmin;
import static com.locgym.apoio.Cenarios.cpfValido;
import static com.locgym.apoio.Cenarios.dadosAluno;
import static com.locgym.apoio.Cenarios.emailUnico;
import static com.locgym.apoio.Cenarios.novoAdministrador;
import static org.assertj.core.api.Assertions.assertThat;

import com.locgym.TestcontainersConfig;
import com.locgym.TestcontainersConfig.BancoSuperusuario;
import com.locgym.apoio.Cenarios.Admin;
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
class AlunosIT {

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

    /** Aluno recém-criado faz login, troca a senha inicial e recebe um cliente no contexto ALUNO. */
    private ClienteHttp entrarComoAluno(String email) {
        String token = http.post("/api/v1/auth/login", Map.of("email", email, "senha", "inicial-123")).texto("accessToken");
        String novo = http.comToken(token).post("/api/v1/auth/trocar-senha",
                Map.of("senhaAtual", "inicial-123", "novaSenha", "minha-senha-1")).texto("accessToken");
        return http.comToken(novo).comContexto(admin.academiaId(), "ALUNO");
    }

    @Test
    void crudCompleto() {
        String email = emailUnico("aluno");
        var criado = ctx.post("/api/v1/alunos", dadosAluno("Ana Souza", email, "529.982.247-25"));
        assertThat(criado.status()).isEqualTo(201);
        assertThat(criado.texto("status")).isEqualTo("ATIVO");
        assertThat(criado.texto("cpf")).isEqualTo("52998224725");
        assertThat(criado.texto("email")).isEqualTo(email);
        assertThat(criado.texto("cep")).isEqualTo("20040002");
        String id = criado.texto("id");

        var dados = dadosAluno("Ana S. Lima", email, "52998224725");
        assertThat(ctx.put("/api/v1/alunos/" + id, dados).status()).isEqualTo(200);
        assertThat(ctx.get("/api/v1/alunos/" + id).texto("nome")).isEqualTo("Ana S. Lima");
        assertThat(ctx.get("/api/v1/alunos").corpo().get("total").asLong()).isEqualTo(1);
    }

    @Test
    void cpfInvalido400() {
        var r = ctx.post("/api/v1/alunos", dadosAluno("X", emailUnico("x"), "52998224724"));
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.corpo().get("erros").get(0).get("campo").asString()).isEqualTo("cpf");
    }

    @Test
    void cpfDuplicadoNaAcademia409() {
        String cpf = cpfValido();
        ctx.post("/api/v1/alunos", dadosAluno("Primeiro", emailUnico("a"), cpf));
        var r = ctx.post("/api/v1/alunos", dadosAluno("Segundo", emailUnico("b"), cpf));
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.texto("codigo")).isEqualTo("CPF_JA_CADASTRADO");
    }

    @Test
    void mesmoCpfEmOutraAcademiaPermitido() {
        String cpf = cpfValido();
        ctx.post("/api/v1/alunos", dadosAluno("Aqui", emailUnico("a"), cpf));
        ClienteHttp outra = comoAdmin(http, novoAdministrador(http));
        assertThat(outra.post("/api/v1/alunos", dadosAluno("Lá", emailUnico("b"), cpf)).status()).isEqualTo(201);
    }

    @Test
    void emailExistenteLigaSemRevelar() {
        Admin dono = novoAdministrador(http);   // conta existente, de outra academia

        var paraExistente = ctx.post("/api/v1/alunos", dadosAluno("Dono Treinando", dono.email(), cpfValido()));
        var paraNovo = ctx.post("/api/v1/alunos", dadosAluno("Novato", emailUnico("novo"), cpfValido()));

        assertThat(paraExistente.status()).isEqualTo(201);
        assertThat(paraNovo.status()).isEqualTo(201);
        assertThat(paraExistente.corpo().propertyNames()).containsExactlyInAnyOrderElementsOf(paraNovo.corpo().propertyNames());
        var login = http.post("/api/v1/auth/login", Map.of("email", dono.email(), "senha", SENHA));
        assertThat(login.status()).as("senha antiga continua valendo").isEqualTo(200);
        assertThat(login.corpo().get("vinculos").toString()).contains(admin.academiaId().toString()).contains("ALUNO");
    }

    @Test
    void mesmoUsuarioDuasVezesNaAcademia409() {
        String email = emailUnico("dup");
        ctx.post("/api/v1/alunos", dadosAluno("Uma", email, cpfValido()));
        var r = ctx.post("/api/v1/alunos", dadosAluno("Duas", email, cpfValido()));
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.texto("codigo")).isEqualTo("ALUNO_JA_CADASTRADO");
    }

    @Test
    void putNaoAlteraEmail() {
        String email = emailUnico("fixo");
        String id = ctx.post("/api/v1/alunos", dadosAluno("Fixo", email, cpfValido())).texto("id");

        var dados = dadosAluno("Fixo", emailUnico("outro"), cpfValido());
        ctx.put("/api/v1/alunos/" + id, dados);

        assertThat(ctx.get("/api/v1/alunos/" + id).texto("email")).isEqualTo(email);
    }

    @Test
    void buscaPorNomeCpfEmail() {
        String email = emailUnico("helio");
        String cpf = cpfValido();
        ctx.post("/api/v1/alunos", dadosAluno("Hélio Gracie", email, cpf));
        ctx.post("/api/v1/alunos", dadosAluno("Royce Silva", emailUnico("royce"), cpfValido()));

        assertThat(ctx.get("/api/v1/alunos?busca=royce").corpo().get("total").asLong()).isEqualTo(1);
        assertThat(ctx.get("/api/v1/alunos?busca=" + cpf.substring(0, 6)).corpo().get("itens").get(0).get("nome").asString())
                .isEqualTo("Hélio Gracie");
        assertThat(ctx.get("/api/v1/alunos?busca=" + email.substring(0, 10)).corpo().get("total").asLong()).isEqualTo(1);
    }

    @Test
    void isolamento() {
        ClienteHttp outra = comoAdmin(http, novoAdministrador(http));
        String id = outra.post("/api/v1/alunos", dadosAluno("Oculto", emailUnico("oc"), cpfValido())).texto("id");

        assertThat(ctx.get("/api/v1/alunos/" + id).status()).isEqualTo(404);
        assertThat(ctx.put("/api/v1/alunos/" + id, dadosAluno("X", emailUnico("x"), cpfValido())).status()).isEqualTo(404);
        assertThat(ctx.patch("/api/v1/alunos/" + id + "/status", Map.of("status", "INATIVO")).status()).isEqualTo(404);
        assertThat(ctx.get("/api/v1/alunos?busca=Oculto").corpo().get("total").asLong()).isZero();
    }

    @Test
    void statusAuditado() {
        String id = ctx.post("/api/v1/alunos", dadosAluno("Auditado", emailUnico("au"), cpfValido())).texto("id");
        ctx.patch("/api/v1/alunos/" + id + "/status", Map.of("status", "INATIVO"));

        assertThat(su.jdbc().queryForObject(
                "select depois->>'status' from auditoria where entidade_id = ?::uuid and acao = 'ALUNO_STATUS_ALTERADO'",
                String.class, id)).isEqualTo("INATIVO");
    }

    @Test
    void meusDadosDoAluno() {
        String email = emailUnico("meus");
        ctx.post("/api/v1/alunos", dadosAluno("Bia Ramos", email, cpfValido()));
        ClienteHttp aluno = entrarComoAluno(email);

        var r = aluno.get("/api/v1/eu/aluno");

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.texto("nome")).isEqualTo("Bia Ramos");
        assertThat(r.texto("email")).isEqualTo(email);
        assertThat(http.comToken(admin.token()).comContexto(admin.academiaId(), "ADMIN").get("/api/v1/eu/aluno").status())
                .isEqualTo(403);
        assertThat(aluno.get("/api/v1/alunos").status()).as("aluno não usa rotas de admin").isEqualTo(403);
    }

    @Test
    void acessoConformeStatusDoAluno() {
        String email = emailUnico("status");
        String id = ctx.post("/api/v1/alunos", dadosAluno("Caio", email, cpfValido())).texto("id");
        ClienteHttp aluno = entrarComoAluno(email);

        ctx.patch("/api/v1/alunos/" + id + "/status", Map.of("status", "INATIVO"));
        assertThat(aluno.get("/api/v1/eu/aluno").status()).as("inativo ainda consulta").isEqualTo(200);

        ctx.patch("/api/v1/alunos/" + id + "/status", Map.of("status", "CANCELADO"));
        var cancelado = aluno.get("/api/v1/eu/aluno");
        assertThat(cancelado.status()).isEqualTo(403);
        assertThat(cancelado.texto("codigo")).isEqualTo("SEM_VINCULO");
        assertThat(aluno.get("/api/v1/eu/vinculos").corpo().size()).isZero();

        ctx.patch("/api/v1/alunos/" + id + "/status", Map.of("status", "ATIVO"));
        assertThat(aluno.get("/api/v1/eu/aluno").status()).as("reativado volta a ter acesso").isEqualTo(200);
    }

    @Test
    void adminEAlunoNaMesmaAcademia() {
        ctx.post("/api/v1/alunos", dadosAluno("Dono Também Treina", admin.email(), cpfValido()));

        assertThat(http.comToken(admin.token()).comContexto(admin.academiaId(), "ADMIN").get("/api/v1/alunos").status())
                .isEqualTo(200);
        assertThat(http.comToken(admin.token()).comContexto(admin.academiaId(), "ALUNO").get("/api/v1/eu/aluno").status())
                .isEqualTo(200);
    }
}
