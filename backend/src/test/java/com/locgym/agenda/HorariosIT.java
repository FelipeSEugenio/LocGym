package com.locgym.agenda;

import static com.locgym.apoio.Cenarios.comoAdmin;
import static com.locgym.apoio.Cenarios.novoAdministrador;
import static org.assertj.core.api.Assertions.assertThat;

import com.locgym.TestcontainersConfig;
import com.locgym.TestcontainersConfig.BancoSuperusuario;
import com.locgym.apoio.Cenarios.Admin;
import com.locgym.apoio.ClienteHttp;
import com.locgym.compartilhado.tenancy.DadosDeTeste;
import java.time.LocalDate;
import java.util.LinkedHashMap;
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
        properties = {"locgym.outbox.relay-habilitado=false", "locgym.rotina-diaria.habilitada=false"})
@Import(TestcontainersConfig.class)
class HorariosIT {

    @LocalServerPort int porta;
    @Autowired JsonMapper json;
    @Autowired BancoSuperusuario su;

    ClienteHttp http;
    Admin admin;
    ClienteHttp ctx;
    UUID professor;

    @BeforeEach
    void preparar() {
        http = new ClienteHttp(porta, json);
        admin = novoAdministrador(http);
        ctx = comoAdmin(http, admin);
        professor = DadosDeTeste.professor(su.jdbc(), admin.academiaId(), "ATIVO");
    }

    Map<String, Object> dados(UUID professorId, int dia, String inicio, String fim, String tipo) {
        var m = new LinkedHashMap<String, Object>();
        m.put("professorId", professorId.toString());
        m.put("diaSemana", dia);
        m.put("inicio", inicio);
        m.put("fim", fim);
        m.put("tipo", tipo);
        return m;
    }

    String criar(int dia, String inicio, String fim, String tipo) {
        var r = ctx.post("/api/v1/horarios", dados(professor, dia, inicio, fim, tipo));
        assertThat(r.status()).as(r.corpo().toString()).isEqualTo(201);
        return r.texto("id");
    }

    @Test
    void criaListaEEdita() {
        String quarta = criar(3, "07:00", "08:00", "No-Gi");
        criar(1, "20:00", "21:00", "Gi");
        criar(1, "19:00", "20:00", "Kids");

        var lista = ctx.get("/api/v1/horarios").corpo();
        assertThat(lista.size()).isEqualTo(3);
        assertThat(lista.get(0).get("inicio").asString()).isEqualTo("19:00");
        assertThat(lista.get(0).get("diaSemana").asInt()).isEqualTo(1);
        assertThat(lista.get(2).get("id").asString()).isEqualTo(quarta);
        assertThat(lista.get(0).get("professorNome").asString()).startsWith("Prof ");
        assertThat(lista.get(0).get("status").asString()).isEqualTo("ATIVO");
        assertThat(ctx.get("/api/v1/horarios?diaSemana=3").corpo().size()).isEqualTo(1);

        var alterar = dados(professor, 3, "07:30", "08:30", "Gi");
        alterar.put("status", "INATIVO");
        var r = ctx.put("/api/v1/horarios/" + quarta, alterar);
        assertThat(r.status()).as(r.corpo().toString()).isEqualTo(200);
        var lido = ctx.get("/api/v1/horarios/" + quarta);
        assertThat(lido.texto("inicio")).isEqualTo("07:30");
        assertThat(lido.texto("status")).isEqualTo("INATIVO");
        assertThat(ctx.get("/api/v1/horarios?status=INATIVO").corpo().size()).isEqualTo(1);
    }

    @Test
    void validacao400() {
        assertThat(ctx.post("/api/v1/horarios", dados(professor, 1, "20:00", "19:00", "Gi")).status()).isEqualTo(400);
        assertThat(ctx.post("/api/v1/horarios", dados(professor, 8, "19:00", "20:00", "Gi")).status()).isEqualTo(400);
        assertThat(ctx.post("/api/v1/horarios", dados(professor, 1, "19:00", "20:00", " ")).status()).isEqualTo(400);
    }

    @Test
    void professorInexistente404() {
        assertThat(ctx.post("/api/v1/horarios", dados(UUID.randomUUID(), 1, "19:00", "20:00", "Gi")).status())
                .isEqualTo(404);
    }

    @Test
    void professorInativo409() {
        UUID inativo = DadosDeTeste.professor(su.jdbc(), admin.academiaId(), "INATIVO");
        var r = ctx.post("/api/v1/horarios", dados(inativo, 1, "19:00", "20:00", "Gi"));
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.texto("codigo")).isEqualTo("PROFESSOR_INATIVO");
    }

    @Test
    void trocarParaProfessorInativo409() {
        String id = criar(1, "19:00", "20:00", "Gi");
        UUID inativo = DadosDeTeste.professor(su.jdbc(), admin.academiaId(), "INATIVO");
        var alterar = dados(inativo, 1, "19:00", "20:00", "Gi");
        alterar.put("status", "ATIVO");
        assertThat(ctx.put("/api/v1/horarios/" + id, alterar).texto("codigo")).isEqualTo("PROFESSOR_INATIVO");
    }

    @Test
    void manterProfessorQueFicouInativoOk() {
        String id = criar(1, "19:00", "20:00", "Gi");
        su.jdbc().update("update professor set status = 'INATIVO' where id = ?", professor);
        var alterar = dados(professor, 1, "19:30", "20:30", "Gi");
        alterar.put("status", "ATIVO");
        assertThat(ctx.put("/api/v1/horarios/" + id, alterar).status()).isEqualTo(200);
    }

    @Test
    void excluirSemAulas204() {
        String id = criar(1, "19:00", "20:00", "Gi");
        assertThat(ctx.delete("/api/v1/horarios/" + id).status()).isEqualTo(204);
        assertThat(ctx.get("/api/v1/horarios/" + id).status()).isEqualTo(404);
    }

    @Test
    void excluirComAulas409() {
        String id = criar(1, "19:00", "20:00", "Gi");
        DadosDeTeste.aula(su.jdbc(), UUID.fromString(id), LocalDate.of(2026, 11, 23));
        var r = ctx.delete("/api/v1/horarios/" + id);
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.texto("codigo")).isEqualTo("HORARIO_COM_AULAS");
    }

    @Test
    void isolamento() {
        String id = criar(1, "19:00", "20:00", "Gi");
        ClienteHttp b = comoAdmin(http, novoAdministrador(http));
        UUID professorB = DadosDeTeste.professor(su.jdbc(), admin.academiaId(), "ATIVO");
        var alterar = dados(professorB, 1, "19:00", "20:00", "Gi");
        alterar.put("status", "ATIVO");

        assertThat(b.get("/api/v1/horarios/" + id).status()).isEqualTo(404);
        assertThat(b.put("/api/v1/horarios/" + id, alterar).status()).isEqualTo(404);
        assertThat(b.delete("/api/v1/horarios/" + id).status()).isEqualTo(404);
        assertThat(b.get("/api/v1/horarios").corpo().size()).isZero();
        // professor de outra academia não é visível
        assertThat(b.post("/api/v1/horarios", dados(professor, 1, "19:00", "20:00", "Gi")).status()).isEqualTo(404);
    }

    @Test
    void auditado() {
        String id = criar(1, "19:00", "20:00", "Gi");
        var alterar = dados(professor, 1, "19:30", "20:30", "Gi");
        alterar.put("status", "ATIVO");
        ctx.put("/api/v1/horarios/" + id, alterar);
        ctx.delete("/api/v1/horarios/" + id);

        assertThat(su.jdbc().queryForList(
                "select acao from auditoria where entidade_id = ?::uuid order by ocorrido_em", String.class, id))
                .containsExactly("HORARIO_CRIADO", "HORARIO_ALTERADO", "HORARIO_EXCLUIDO");
    }

    @Test
    void academiaInativaBloqueiaEscrita409() {
        su.jdbc().update("update academia set status = 'INATIVA' where id = ?", admin.academiaId());
        var r = ctx.post("/api/v1/horarios", dados(professor, 1, "19:00", "20:00", "Gi"));
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.texto("codigo")).isEqualTo("ACADEMIA_INATIVA");
    }
}
