package com.locgym.matricula;

import static com.locgym.apoio.Cenarios.comoAdmin;
import static com.locgym.apoio.Cenarios.novoAdministrador;
import static java.time.LocalDate.of;
import static org.assertj.core.api.Assertions.assertThat;

import com.locgym.TestcontainersConfig;
import com.locgym.TestcontainersConfig.BancoSuperusuario;
import com.locgym.apoio.Cenarios.Admin;
import com.locgym.apoio.ClienteHttp;
import com.locgym.apoio.CalendarioDeTeste;
import com.locgym.compartilhado.tenancy.DadosDeTeste;
import com.locgym.pagamento.CobrancaQuitada;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"locgym.outbox.relay-habilitado=false", "locgym.rotina-diaria.habilitada=false"})
@Import({TestcontainersConfig.class, CalendarioDeTeste.Config.class, MatriculasIT.Sabotador.class})
class MatriculasIT {

    /** Faz o tratamento da quitação falhar para as matrículas marcadas. */
    @TestConfiguration(proxyBeanMethods = false)
    static class Sabotador {
        @Bean
        Set<UUID> matriculasSabotadas() {
            return ConcurrentHashMap.newKeySet();
        }

        @Bean
        Object sabotadorDeQuitacao(Set<UUID> matriculasSabotadas) {
            return new Object() {
                @EventListener
                void aoQuitar(CobrancaQuitada e) {
                    if (matriculasSabotadas.contains(e.matriculaId())) {
                        throw new IllegalStateException("falha simulada");
                    }
                }
            };
        }
    }

    static final LocalDate HOJE = of(2026, 11, 15);

    @LocalServerPort int porta;
    @Autowired JsonMapper json;
    @Autowired BancoSuperusuario su;
    @Autowired CalendarioDeTeste relogio;
    @Autowired Set<UUID> matriculasSabotadas;

    ClienteHttp http;
    Admin admin;
    ClienteHttp ctx;
    UUID aluno;
    UUID plano;

    @BeforeEach
    void preparar() {
        relogio.definir(HOJE);
        http = new ClienteHttp(porta, json);
        admin = novoAdministrador(http);
        ctx = comoAdmin(http, admin);
        aluno = novoAluno("ATIVO");
        plano = DadosDeTeste.plano(su.jdbc(), admin.academiaId(), "Mensal");   // R$ 150,00 mensal
    }

    UUID novoAluno(String status) {
        UUID usuario = DadosDeTeste.usuario(su.jdbc(), "aluno-" + UUID.randomUUID() + "@teste.com");
        return DadosDeTeste.aluno(su.jdbc(), usuario, admin.academiaId(), status);
    }

    ClienteHttp.Resposta criar(UUID aluno, LocalDate inicio) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("alunoId", aluno.toString());
        corpo.put("planoId", plano.toString());
        corpo.put("dataInicio", inicio.toString());
        return ctx.post("/api/v1/matriculas", corpo);
    }

    String criarOk(LocalDate inicio) {
        var r = criar(aluno, inicio);
        assertThat(r.status()).as(r.corpo().toString()).isEqualTo(201);
        return r.texto("id");
    }

    JsonNode detalhe(String id) {
        return ctx.get("/api/v1/matriculas/" + id).corpo();
    }

    /** Cobranças da matrícula com o status dado, em ordem. */
    List<JsonNode> cobrancas(String id, String status) {
        var lista = new java.util.ArrayList<JsonNode>();
        detalhe(id).get("cobrancas").forEach(c -> {
            if (status == null || status.equals(c.get("status").asString())) lista.add(c);
        });
        return lista;
    }

    ClienteHttp.Resposta pagar(String cobrancaId) {
        return ctx.post("/api/v1/cobrancas/" + cobrancaId + "/pagamento",
                Map.of("dataPagamento", relogioHoje().toString(), "forma", "PIX"));
    }

    LocalDate relogioHoje() {
        return relogio.hoje();
    }

    void pagarMaisAntiga(String id) {
        var aberta = detalhe(id).get("cobrancas").valueStream()
                .filter(c -> Set.of("PENDENTE", "ATRASADO").contains(c.get("status").asString())).findFirst().orElseThrow();
        var r = pagar(aberta.get("id").asString());
        assertThat(r.status()).as(r.corpo().toString()).isEqualTo(200);
    }

    int eventos(String tipo, String matriculaId) {
        return su.jdbc().queryForObject(
                "select count(*) from outbox_evento where tipo = ? and payload->'dados'->>'matriculaId' = ?",
                Integer.class, tipo, matriculaId);
    }

    int auditorias(String acao, String id) {
        return su.jdbc().queryForObject("select count(*) from auditoria where acao = ? and entidade_id = ?::uuid",
                Integer.class, acao, id);
    }

    @Test
    void criarGeraPrimeiraCobrancaEEventos() {
        var r = criar(aluno, HOJE);

        assertThat(r.status()).isEqualTo(201);
        assertThat(r.texto("status")).isEqualTo("PENDENTE");
        assertThat(r.corpo().get("aluno").get("id").asString()).isEqualTo(aluno.toString());
        assertThat(r.corpo().get("aluno").get("nome").asString()).isNotBlank();
        assertThat(r.corpo().get("plano").get("nome").asString()).isEqualTo("Mensal");
        assertThat(r.texto("vigenteAte")).isEqualTo(HOJE.toString());
        String id = r.texto("id");
        var cobs = cobrancas(id, null);
        assertThat(cobs).singleElement().satisfies(c -> {
            assertThat(c.get("status").asString()).isEqualTo("PENDENTE");
            assertThat(c.get("vencimento").asString()).isEqualTo(HOJE.toString());
            assertThat(c.get("periodoFim").asString()).isEqualTo(HOJE.plusMonths(1).toString());
        });
        assertThat(eventos("matricula.criada", id)).isEqualTo(1);
        assertThat(eventos("pagamento.gerado", id)).isEqualTo(1);
        assertThat(auditorias("MATRICULA_CRIADA", id)).isEqualTo(1);
    }

    @Test
    void valorPadraoDoPlanoOuInformado() {
        assertThat(detalhe(criarOk(HOJE)).get("valor").decimalValue()).isEqualByComparingTo("150.00");

        Map<String, Object> corpo = new HashMap<>(Map.of("alunoId", novoAluno("ATIVO").toString(),
                "planoId", plano.toString(), "dataInicio", HOJE.toString(), "valor", new java.math.BigDecimal("99.90")));
        var r = ctx.post("/api/v1/matriculas", corpo);
        assertThat(r.corpo().get("valor").decimalValue()).isEqualByComparingTo("99.90");
        assertThat(cobrancas(r.texto("id"), null).get(0).get("valor").decimalValue()).isEqualByComparingTo("99.90");
    }

    @Test
    void criarRegras() {
        var inativo = criar(novoAluno("INATIVO"), HOJE);
        assertThat(inativo.status()).isEqualTo(409);
        assertThat(inativo.texto("codigo")).isEqualTo("ALUNO_NAO_ATIVO");

        su.jdbc().update("update plano set status = 'INATIVO' where id = ?", plano);
        assertThat(criar(aluno, HOJE).texto("codigo")).isEqualTo("PLANO_NAO_ATIVO");
        su.jdbc().update("update plano set status = 'ATIVO' where id = ?", plano);

        assertThat(criar(UUID.randomUUID(), HOJE).status()).isEqualTo(404);
        assertThat(criar(aluno, HOJE.plusDays(31)).status()).isEqualTo(400);

        String primeira = criarOk(HOJE);
        var segunda = criar(aluno, HOJE);
        assertThat(segunda.status()).isEqualTo(409);
        assertThat(segunda.texto("codigo")).isEqualTo("MATRICULA_JA_EXISTE");

        ctx.post("/api/v1/matriculas/" + primeira + "/cancelar", Map.of("motivo", "trocou de plano"));
        assertThat(criar(aluno, HOJE).status()).isEqualTo(201);
    }

    @Test
    void matriculaConcorrenteUmSucessoUm409() {
        var f1 = CompletableFuture.supplyAsync(() -> criar(aluno, HOJE));
        var f2 = CompletableFuture.supplyAsync(() -> criar(aluno, HOJE));
        var r1 = f1.join();
        var r2 = f2.join();

        assertThat(List.of(r1.status(), r2.status())).containsExactlyInAnyOrder(201, 409);
        assertThat((r1.status() == 409 ? r1 : r2).texto("codigo")).isEqualTo("MATRICULA_JA_EXISTE");
        assertThat(su.jdbc().queryForObject("select count(*) from matricula where aluno_id = ?", Integer.class, aluno))
                .isEqualTo(1);
    }

    @Test
    void pagarPrimeiraAtiva() {
        String id = criarOk(HOJE);

        pagarMaisAntiga(id);

        var d = detalhe(id);
        assertThat(d.get("status").asString()).isEqualTo("ATIVA");
        assertThat(d.get("vigenteAte").asString()).isEqualTo(HOJE.plusMonths(1).toString());
        assertThat(d.get("periodos").size()).isEqualTo(1);
        assertThat(eventos("matricula.renovada", id)).isZero();
    }

    @Test
    void cicloDeTresMeses() {
        String id = criarOk(HOJE);
        pagarMaisAntiga(id);
        for (int i = 1; i <= 2; i++) {
            relogio.definir(HOJE.plusMonths(i).minusDays(2));
            assertThat(ctx.post("/api/v1/matriculas/" + id + "/renovar", Map.of()).status()).isEqualTo(200);
            pagarMaisAntiga(id);
        }

        var d = detalhe(id);
        assertThat(d.get("status").asString()).isEqualTo("ATIVA");
        assertThat(d.get("vigenteAte").asString()).isEqualTo(HOJE.plusMonths(3).toString());
        var periodos = d.get("periodos");
        assertThat(periodos.size()).isEqualTo(3);
        for (int i = 1; i < 3; i++) {
            assertThat(periodos.get(i).get("inicio").asString()).isEqualTo(periodos.get(i - 1).get("fim").asString());
        }
        assertThat(eventos("matricula.renovada", id)).isEqualTo(2);
    }

    @Test
    void voltaDepoisDeTresMeses() {
        relogio.definir(of(2026, 7, 15));
        String id = criarOk(of(2026, 7, 10));
        pagarMaisAntiga(id);                                                     // vigente até 10/08
        ctx.post("/api/v1/matriculas/" + id + "/renovar", Map.of());             // cobrança de 10/08
        relogio.definir(HOJE);                                                // 15/11
        su.jdbc().update("update pagamento set status = 'ATRASADO' where matricula_id = ?::uuid and status = 'PENDENTE'", id);
        su.jdbc().update("update matricula set status = 'VENCIDA' where id = ?::uuid", id);

        pagarMaisAntiga(id);                                                     // paga a de 10/08

        assertThat(detalhe(id).get("status").asString()).isEqualTo("VENCIDA");
        var atrasadas = cobrancas(id, "ATRASADO");
        assertThat(atrasadas).extracting(c -> c.get("vencimento").asString())
                .containsExactly("2026-09-10", "2026-10-10", "2026-11-10");

        var abono = ctx.post("/api/v1/matriculas/" + id + "/abonar",
                Map.of("ateCobrancaId", atrasadas.get(1).get("id").asString(), "motivo", "Meses parados"));
        assertThat(abono.status()).as(abono.corpo().toString()).isEqualTo(200);
        assertThat(abono.texto("status")).isEqualTo("VENCIDA");
        assertThat(auditorias("COBRANCAS_ABONADAS", id)).isEqualTo(1);

        pagarMaisAntiga(id);                                                     // paga a de 10/11

        var d = detalhe(id);
        assertThat(d.get("status").asString()).isEqualTo("ATIVA");
        assertThat(d.get("vigenteAte").asString()).isEqualTo("2026-12-10");
        assertThat(d.get("periodos").size()).isEqualTo(5);
    }

    @Test
    void rollbackDoListenerDesfazPagamento() {
        String id = criarOk(HOJE);
        matriculasSabotadas.add(UUID.fromString(id));
        String cobranca = cobrancas(id, null).get(0).get("id").asString();

        assertThat(pagar(cobranca).status()).isEqualTo(500);

        matriculasSabotadas.clear();
        assertThat(cobrancas(id, "PENDENTE")).hasSize(1);
        assertThat(detalhe(id).get("status").asString()).isEqualTo("PENDENTE");
        assertThat(eventos("pagamento.registrado", id)).isZero();
    }

    @Test
    void suspenderEReativar() {
        String id = criarOk(HOJE);
        pagarMaisAntiga(id);                                                      // vigente até 15/12
        ctx.post("/api/v1/matriculas/" + id + "/renovar", Map.of());              // pendente 15/12

        assertThat(ctx.post("/api/v1/matriculas/" + id + "/suspender", Map.of("motivo", " ")).status()).isEqualTo(400);
        var s = ctx.post("/api/v1/matriculas/" + id + "/suspender", Map.of("motivo", "Viagem"));
        assertThat(s.texto("status")).isEqualTo("SUSPENSA");
        assertThat(s.texto("suspensaDesde")).isEqualTo(HOJE.toString());

        relogio.avancarDias(12);
        var r = ctx.post("/api/v1/matriculas/" + id + "/reativar", Map.of());

        assertThat(r.texto("status")).isEqualTo("ATIVA");
        assertThat(r.texto("vigenteAte")).isEqualTo(of(2026, 12, 27).toString());
        var pendente = cobrancas(id, "PENDENTE").get(0);
        assertThat(pendente.get("vencimento").asString()).isEqualTo("2026-12-27");
        assertThat(pendente.get("periodoFim").asString()).isEqualTo("2027-01-27");
        assertThat(r.corpo().get("periodos").get(0).get("fim").asString()).isEqualTo("2026-12-27");
        assertThat(eventos("matricula.suspensa", id)).isEqualTo(1);
        assertThat(eventos("matricula.reativada", id)).isEqualTo(1);
        assertThat(auditorias("MATRICULA_SUSPENSA", id)).isEqualTo(1);
        assertThat(auditorias("MATRICULA_REATIVADA", id)).isEqualTo(1);
    }

    @Test
    void pagarDuranteSuspensao() {
        String id = criarOk(HOJE);
        pagarMaisAntiga(id);
        ctx.post("/api/v1/matriculas/" + id + "/renovar", Map.of());
        su.jdbc().update("update pagamento set status = 'ATRASADO' where matricula_id = ?::uuid and status = 'PENDENTE'", id);
        ctx.post("/api/v1/matriculas/" + id + "/suspender", Map.of("motivo", "Lesão"));

        pagarMaisAntiga(id);

        var d = detalhe(id);
        assertThat(d.get("status").asString()).isEqualTo("SUSPENSA");
        assertThat(d.get("vigenteAte").asString()).isEqualTo(HOJE.plusMonths(2).toString());
        assertThat(cobrancas(id, null)).hasSize(2);
    }

    @Test
    void cancelarPreservaAtrasadas() {
        String id = criarOk(HOJE);
        pagarMaisAntiga(id);
        ctx.post("/api/v1/matriculas/" + id + "/renovar", Map.of());
        su.jdbc().update("update pagamento set status = 'ATRASADO' where matricula_id = ?::uuid and status = 'PENDENTE'", id);
        DadosDeTeste.cobranca(su.jdbc(), admin.academiaId(), UUID.fromString(id), aluno, HOJE.plusMonths(2));

        var c = ctx.post("/api/v1/matriculas/" + id + "/cancelar", Map.of("motivo", "Mudou de cidade"));

        assertThat(c.texto("status")).isEqualTo("CANCELADA");
        assertThat(c.texto("motivo")).isEqualTo("Mudou de cidade");
        assertThat(cobrancas(id, "ATRASADO")).hasSize(1);
        assertThat(cobrancas(id, "CANCELADO")).singleElement()
                .satisfies(x -> assertThat(x.get("motivoCancelamento").asString()).isEqualTo("Matrícula cancelada"));
        assertThat(eventos("matricula.cancelada", id)).isEqualTo(1);

        pagarMaisAntiga(id);

        var d = detalhe(id);
        assertThat(d.get("status").asString()).isEqualTo("CANCELADA");
        assertThat(d.get("vigenteAte").asString()).isEqualTo(HOJE.plusMonths(1).toString());
        assertThat(d.get("periodos").size()).isEqualTo(1);
        assertThat(eventos("matricula.renovada", id)).isZero();
        assertThat(ctx.post("/api/v1/matriculas/" + id + "/cancelar", Map.of("motivo", "x")).texto("codigo"))
                .isEqualTo("MATRICULA_JA_CANCELADA");
    }

    @Test
    void renovarManual() {
        String id = criarOk(HOJE);
        assertThat(ctx.post("/api/v1/matriculas/" + id + "/renovar", Map.of()).texto("codigo")).isEqualTo("MATRICULA_NAO_ATIVA");
        pagarMaisAntiga(id);

        var r = ctx.post("/api/v1/matriculas/" + id + "/renovar", Map.of());

        assertThat(r.status()).isEqualTo(200);
        assertThat(cobrancas(id, "PENDENTE")).singleElement()
                .satisfies(c -> assertThat(c.get("vencimento").asString()).isEqualTo(HOJE.plusMonths(1).toString()));
        assertThat(auditorias("MATRICULA_RENOVADA", id)).isEqualTo(1);
        var deNovo = ctx.post("/api/v1/matriculas/" + id + "/renovar", Map.of());
        assertThat(deNovo.status()).isEqualTo(409);
        assertThat(deNovo.texto("codigo")).isEqualTo("COBRANCA_EM_ABERTO");
    }

    @Test
    void matriculaDoAluno() {
        String id = criarOk(HOJE);

        var r = ctx.get("/api/v1/alunos/" + aluno + "/matricula");

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.texto("id")).isEqualTo(id);
        assertThat(r.corpo().get("inadimplente").asBoolean()).isFalse();
        assertThat(ctx.get("/api/v1/alunos/" + novoAluno("ATIVO") + "/matricula").status()).isEqualTo(404);
    }

    @Test
    void listarComFiltroEBusca() {
        criarOk(HOJE);
        UUID outro = novoAluno("ATIVO");
        su.jdbc().update("update aluno set nome = 'Zé 100% Faixa_Preta' where id = ?", outro);
        var r = criar(outro, HOJE);
        pagarMaisAntiga(r.texto("id"));

        assertThat(ctx.get("/api/v1/matriculas").corpo().get("total").asInt()).isEqualTo(2);
        var ativas = ctx.get("/api/v1/matriculas?status=ATIVA").corpo();
        assertThat(ativas.get("total").asInt()).isEqualTo(1);
        assertThat(ativas.get("itens").get(0).get("alunoNome").asString()).isEqualTo("Zé 100% Faixa_Preta");
        assertThat(ativas.get("itens").get(0).get("planoNome").asString()).isEqualTo("Mensal");
        var sublinhado = ctx.get("/api/v1/matriculas?busca=_");
        assertThat(sublinhado.corpo().get("total").asInt()).as(sublinhado.corpo().toString()).isEqualTo(1);
    }

    @Test
    void isolamento() {
        String id = criarOk(HOJE);
        ClienteHttp b = comoAdmin(http, novoAdministrador(http));

        assertThat(b.get("/api/v1/matriculas/" + id).status()).isEqualTo(404);
        for (String acao : List.of("renovar", "reativar")) {
            assertThat(b.post("/api/v1/matriculas/" + id + "/" + acao, Map.of()).status()).isEqualTo(404);
        }
        for (String acao : List.of("suspender", "cancelar")) {
            assertThat(b.post("/api/v1/matriculas/" + id + "/" + acao, Map.of("motivo", "x")).status()).isEqualTo(404);
        }
        assertThat(b.post("/api/v1/matriculas/" + id + "/abonar",
                Map.of("ateCobrancaId", UUID.randomUUID().toString(), "motivo", "x")).status()).isEqualTo(404);
        assertThat(b.get("/api/v1/alunos/" + aluno + "/matricula").status()).isEqualTo(404);
        assertThat(b.get("/api/v1/matriculas").corpo().get("total").asInt()).isZero();
        Map<String, Object> corpo = Map.of("alunoId", aluno.toString(), "planoId", plano.toString(),
                "dataInicio", HOJE.toString());
        assertThat(b.post("/api/v1/matriculas", corpo).status()).isEqualTo(404);
    }
}
