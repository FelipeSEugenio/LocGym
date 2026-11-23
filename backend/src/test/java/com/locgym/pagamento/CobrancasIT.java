package com.locgym.pagamento;

import static com.locgym.apoio.Cenarios.comoAdmin;
import static com.locgym.apoio.Cenarios.novoAdministrador;
import static org.assertj.core.api.Assertions.assertThat;

import com.locgym.TestcontainersConfig;
import com.locgym.TestcontainersConfig.BancoSuperusuario;
import com.locgym.apoio.Cenarios.Admin;
import com.locgym.apoio.ClienteHttp;
import com.locgym.compartilhado.tempo.Calendario;
import com.locgym.compartilhado.tenancy.ContextoTenant;
import com.locgym.compartilhado.tenancy.DadosDeTeste;
import com.locgym.compartilhado.tenancy.Perfil;
import com.locgym.compartilhado.tenancy.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"locgym.outbox.relay-habilitado=false", "locgym.rotina-diaria.habilitada=false"})
@Import({TestcontainersConfig.class, CobrancasIT.Ouvinte.class})
class CobrancasIT {

    /** Captura as quitações e se chegaram dentro da transação do pagamento. */
    @TestConfiguration(proxyBeanMethods = false)
    static class Ouvinte {
        record Recebida(CobrancaQuitada evento, boolean emTransacao) {}

        @Bean
        List<Recebida> quitacoesRecebidas() {
            return new CopyOnWriteArrayList<>();
        }

        @Bean
        Object ouvinteDeQuitacao(List<Recebida> recebidas) {
            return new Object() {
                @EventListener
                void aoQuitar(CobrancaQuitada e) {
                    recebidas.add(new Recebida(e, TransactionSynchronizationManager.isActualTransactionActive()));
                }
            };
        }
    }

    @LocalServerPort int porta;
    @Autowired JsonMapper json;
    @Autowired BancoSuperusuario su;
    @Autowired Cobrancas cobrancas;
    @Autowired TransactionTemplate tx;
    @Autowired Calendario calendario;
    @Autowired List<Ouvinte.Recebida> quitacoes;

    ClienteHttp http;
    Admin admin;
    ClienteHttp ctx;
    UUID aluno;
    UUID matricula;
    LocalDate hoje;

    @BeforeEach
    void preparar() {
        http = new ClienteHttp(porta, json);
        admin = novoAdministrador(http);
        ctx = comoAdmin(http, admin);
        hoje = calendario.hoje();
        UUID usuario = DadosDeTeste.usuario(su.jdbc(), "aluno-" + UUID.randomUUID() + "@teste.com");
        aluno = DadosDeTeste.aluno(su.jdbc(), usuario, admin.academiaId(), "ATIVO");
        UUID plano = DadosDeTeste.plano(su.jdbc(), admin.academiaId(), "Mensal");
        matricula = DadosDeTeste.matricula(su.jdbc(), admin.academiaId(), aluno, plano, hoje.minusMonths(2));
        quitacoes.clear();
    }

    @AfterEach
    void limpar() {
        TenantContext.limpar();
    }

    private <T> T naAcademia(Supplier<T> acao) {
        TenantContext.definir(new ContextoTenant(admin.academiaId(), Perfil.ADMIN, true, admin.usuarioId()));
        try {
            return tx.execute(s -> acao.get());
        } finally {
            TenantContext.limpar();
        }
    }

    private CobrancaResumo gerar(LocalDate inicio) {
        return naAcademia(() -> cobrancas.gerar(matricula, aluno, new BigDecimal("150.00"), inicio, inicio.plusMonths(1)));
    }

    private int eventos(String tipo, UUID cobranca) {
        return su.jdbc().queryForObject(
                "select count(*) from outbox_evento where tipo = ? and payload->'dados'->>'cobrancaId' = ?",
                Integer.class, tipo, cobranca.toString());
    }

    private static Map<String, Object> pagamento(LocalDate data, String forma) {
        return Map.of("dataPagamento", data.toString(), "forma", forma, "observacoes", "pago no balcão");
    }

    @Test
    void geraIdempotente() {
        CobrancaResumo primeira = gerar(hoje);
        CobrancaResumo segunda = gerar(hoje);

        assertThat(segunda.id()).isEqualTo(primeira.id());
        assertThat(primeira.status()).isEqualTo("PENDENTE");
        assertThat(su.jdbc().queryForObject("select count(*) from pagamento where matricula_id = ?", Integer.class,
                matricula)).isEqualTo(1);
        assertThat(eventos("pagamento.gerado", primeira.id())).isEqualTo(1);
    }

    @Test
    void geraAtrasadaQuandoVencimentoPassou() {
        CobrancaResumo c = gerar(hoje.minusDays(5));

        assertThat(c.status()).isEqualTo("ATRASADO");
        assertThat(eventos("pagamento.gerado", c.id())).isEqualTo(1);
        assertThat(eventos("pagamento.atrasado", c.id())).isEqualTo(1);
    }

    @Test
    void registrarPagamentoPagaEPublica() {
        CobrancaResumo c = gerar(hoje);

        var r = ctx.post("/api/v1/cobrancas/" + c.id() + "/pagamento", pagamento(hoje, "PIX"));

        assertThat(r.status()).as(r.corpo().toString()).isEqualTo(200);
        assertThat(r.texto("status")).isEqualTo("PAGO");
        assertThat(r.texto("forma")).isEqualTo("PIX");
        assertThat(eventos("pagamento.registrado", c.id())).isEqualTo(1);
        assertThat(su.jdbc().queryForObject(
                "select count(*) from auditoria where entidade_id = ? and acao = 'PAGAMENTO_REGISTRADO' and usuario_id = ?",
                Integer.class, c.id(), admin.usuarioId())).isEqualTo(1);
        assertThat(quitacoes).singleElement().satisfies(q -> {
            assertThat(q.emTransacao()).isTrue();
            assertThat(q.evento().cobrancaId()).isEqualTo(c.id());
            assertThat(q.evento().matriculaId()).isEqualTo(matricula);
            assertThat(q.evento().periodoInicio()).isEqualTo(hoje);
            assertThat(q.evento().periodoFim()).isEqualTo(hoje.plusMonths(1));
            assertThat(q.evento().abono()).isFalse();
        });
    }

    @Test
    void quitarForaDeOrdem409() {
        CobrancaResumo antiga = gerar(hoje.minusMonths(1));
        CobrancaResumo nova = gerar(hoje);

        var r = ctx.post("/api/v1/cobrancas/" + nova.id() + "/pagamento", pagamento(hoje, "PIX"));

        assertThat(r.status()).isEqualTo(409);
        assertThat(r.texto("codigo")).isEqualTo("QUITAR_MAIS_ANTIGA_PRIMEIRO");
        assertThat(r.texto("detail")).contains(String.format("%02d/%02d/%d",
                antiga.vencimento().getDayOfMonth(), antiga.vencimento().getMonthValue(), antiga.vencimento().getYear()));
        assertThat(ctx.post("/api/v1/cobrancas/" + nova.id() + "/cancelar", Map.of("motivo", "x")).texto("codigo"))
                .isEqualTo("QUITAR_MAIS_ANTIGA_PRIMEIRO");
    }

    @Test
    void dataPagamentoFutura400() {
        CobrancaResumo c = gerar(hoje);
        var r = ctx.post("/api/v1/cobrancas/" + c.id() + "/pagamento", pagamento(hoje.plusDays(1), "PIX"));
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.corpo().get("erros").get(0).get("campo").asString()).isEqualTo("dataPagamento");
    }

    @Test
    void pagamentoAntecipadoAceito() {
        CobrancaResumo c = gerar(hoje.plusDays(5));
        assertThat(ctx.post("/api/v1/cobrancas/" + c.id() + "/pagamento", pagamento(hoje, "DINHEIRO")).status())
                .isEqualTo(200);
    }

    @Test
    void formaInvalida400() {
        CobrancaResumo c = gerar(hoje);
        assertThat(ctx.post("/api/v1/cobrancas/" + c.id() + "/pagamento", pagamento(hoje, "CHEQUE")).status())
                .isEqualTo(400);
        assertThat(ctx.post("/api/v1/cobrancas/" + c.id() + "/pagamento", Map.of("dataPagamento", hoje.toString()))
                .status()).isEqualTo(400);
    }

    @Test
    void abonarExigeMotivoEPublica() {
        CobrancaResumo c = gerar(hoje.minusDays(3));

        assertThat(ctx.post("/api/v1/cobrancas/" + c.id() + "/cancelar", Map.of("motivo", " ")).status()).isEqualTo(400);
        var r = ctx.post("/api/v1/cobrancas/" + c.id() + "/cancelar", Map.of("motivo", "Aluno lesionado"));

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.texto("status")).isEqualTo("CANCELADO");
        assertThat(r.texto("motivoCancelamento")).isEqualTo("Aluno lesionado");
        assertThat(eventos("pagamento.cancelado", c.id())).isEqualTo(1);
        assertThat(su.jdbc().queryForObject(
                "select count(*) from auditoria where entidade_id = ? and acao = 'COBRANCA_ABONADA'", Integer.class,
                c.id())).isEqualTo(1);
        assertThat(quitacoes).singleElement().satisfies(q -> assertThat(q.evento().abono()).isTrue());
    }

    @Test
    void abonarAteAbonaEmOrdem() {
        CobrancaResumo c1 = gerar(hoje.minusMonths(2));
        CobrancaResumo c2 = gerar(hoje.minusMonths(1));
        CobrancaResumo c3 = gerar(hoje);

        List<UUID> abonadas = naAcademia(() -> cobrancas.abonarAte(matricula, c2.id(), "Meses parados"));

        assertThat(abonadas).containsExactly(c1.id(), c2.id());
        assertThat(quitacoes).extracting(q -> q.evento().cobrancaId()).containsExactly(c1.id(), c2.id());
        assertThat(su.jdbc().queryForObject("select status from pagamento where id = ?", String.class, c3.id()))
                .isEqualTo("PENDENTE");
    }

    @Test
    void pagamentoConcorrenteUmSucessoUm409() {
        CobrancaResumo c = gerar(hoje);

        var f1 = CompletableFuture.supplyAsync(() ->
                ctx.post("/api/v1/cobrancas/" + c.id() + "/pagamento", pagamento(hoje, "PIX")));
        var f2 = CompletableFuture.supplyAsync(() ->
                ctx.post("/api/v1/cobrancas/" + c.id() + "/pagamento", pagamento(hoje, "PIX")));
        var r1 = f1.join();
        var r2 = f2.join();

        assertThat(List.of(r1.status(), r2.status())).containsExactlyInAnyOrder(200, 409);
        var conflito = r1.status() == 409 ? r1 : r2;
        assertThat(conflito.texto("codigo")).isIn("CONFLITO_DE_ATUALIZACAO", "COBRANCA_NAO_EM_ABERTO");
        assertThat(eventos("pagamento.registrado", c.id())).isEqualTo(1);
    }

    @Test
    void listarComFiltros() {
        CobrancaResumo atrasada = gerar(hoje.minusMonths(1));
        gerar(hoje.plusDays(2));

        var atrasadas = ctx.get("/api/v1/cobrancas?status=ATRASADO");
        assertThat(atrasadas.corpo().get("total").asInt()).isEqualTo(1);
        var item = atrasadas.corpo().get("itens").get(0);
        assertThat(item.get("id").asString()).isEqualTo(atrasada.id().toString());
        assertThat(item.get("alunoNome").asString()).isNotBlank();

        var porData = ctx.get("/api/v1/cobrancas?vencimentoDe=" + hoje + "&vencimentoAte=" + hoje.plusDays(10));
        assertThat(porData.corpo().get("total").asInt()).isEqualTo(1);
        assertThat(ctx.get("/api/v1/cobrancas?alunoId=" + aluno).corpo().get("total").asInt()).isEqualTo(2);
        assertThat(ctx.get("/api/v1/cobrancas?alunoId=" + UUID.randomUUID()).corpo().get("total").asInt()).isZero();
    }

    @Test
    void isolamento() {
        CobrancaResumo c = gerar(hoje);
        Admin outro = novoAdministrador(http);
        ClienteHttp b = comoAdmin(http, outro);

        assertThat(b.post("/api/v1/cobrancas/" + c.id() + "/pagamento", pagamento(hoje, "PIX")).status()).isEqualTo(404);
        assertThat(b.post("/api/v1/cobrancas/" + c.id() + "/cancelar", Map.of("motivo", "x")).status()).isEqualTo(404);
        assertThat(b.get("/api/v1/cobrancas").corpo().get("total").asInt()).isZero();
    }

    @Test
    void eventosSemDadosPessoais() {
        CobrancaResumo c = gerar(hoje);
        ctx.post("/api/v1/cobrancas/" + c.id() + "/pagamento", pagamento(hoje, "PIX"));
        var dadosAluno = su.jdbc().queryForMap("select a.cpf, u.email from aluno a join usuario u on u.id = a.usuario_id where a.id = ?", aluno);

        List<String> payloads = su.jdbc().queryForList(
                "select payload::text from outbox_evento where payload->'dados'->>'cobrancaId' = ?", String.class,
                c.id().toString());

        assertThat(payloads).hasSizeGreaterThanOrEqualTo(2).allSatisfy(p -> assertThat(p)
                .doesNotContain((String) dadosAluno.get("cpf")).doesNotContain((String) dadosAluno.get("email")));
    }
}
