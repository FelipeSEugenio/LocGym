package com.locgym.frequencia;

import static com.locgym.apoio.Cenarios.comoAdmin;
import static com.locgym.apoio.Cenarios.novoAdministrador;
import static java.time.LocalTime.of;
import static org.assertj.core.api.Assertions.assertThat;

import com.locgym.TestcontainersConfig;
import com.locgym.apoio.CalendarioDeTeste;
import com.locgym.apoio.ClienteHttp;
import com.locgym.compartilhado.tenancy.DadosDeTeste;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"locgym.outbox.relay-habilitado=false", "locgym.rotina-diaria.habilitada=false"})
@Import({TestcontainersConfig.class, CalendarioDeTeste.Config.class})
class CorrecaoIT extends BaseFrequenciaIT {

    ClienteHttp.Resposta excluir(String id, String motivo) {
        return ctx.post("/api/v1/checkins/" + id + "/excluir", Map.of("motivo", motivo));
    }

    ClienteHttp.Resposta corrigir(String id, String data, String hora, UUID horarioId, String motivo) {
        var corpo = new LinkedHashMap<String, Object>(Map.of("data", data, "hora", hora, "motivo", motivo));
        if (horarioId != null) {
            corpo.put("horarioId", horarioId.toString());
        }
        return ctx.post("/api/v1/checkins/" + id + "/corrigir", corpo);
    }

    String status(String id) {
        return su.jdbc().queryForObject("select status from checkin where id = ?::uuid", String.class, id);
    }

    @Test
    void excluirLiberaNovoCheckin() {
        String id = qr(aluno).texto("checkinId");
        assertThat(excluir(id, "lançado errado").status()).isEqualTo(204);
        assertThat(status(id)).isEqualTo("EXCLUIDO");
        assertThat(qr(aluno).status()).isEqualTo(201);
    }

    @Test
    void excluirSemMotivo400() {
        String id = qr(aluno).texto("checkinId");
        assertThat(excluir(id, " ").status()).isEqualTo(400);
        assertThat(status(id)).isEqualTo("ATIVO");
    }

    @Test
    void excluirDuasVezes409() {
        String id = qr(aluno).texto("checkinId");
        excluir(id, "erro");
        var r = excluir(id, "erro de novo");
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.texto("codigo")).isEqualTo("CHECKIN_NAO_ATIVO");
    }

    @Test
    void corrigirParaOutraAula() {
        UUID b = horario(of(20, 0), of(21, 0), "No-Gi");
        String id = qr(aluno).texto("checkinId");
        agora(20, 30);

        var r = corrigir(id, SEGUNDA.toString(), "20:05", b, "era a aula das 20h");

        assertThat(r.status()).as(r.corpo().toString()).isEqualTo(201);
        String novo = r.texto("checkinId");
        assertThat(r.corpo().get("aula").get("horarioId").asString()).isEqualTo(b.toString());
        assertThat(r.texto("pontualidade")).isEqualTo("PONTUAL");
        assertThat(status(id)).isEqualTo("CORRIGIDO");
        assertThat(su.jdbc().queryForObject("select substituido_por from checkin where id = ?::uuid", UUID.class, id))
                .isEqualTo(UUID.fromString(novo));
        assertThat(su.jdbc().queryForObject("select origem from checkin where id = ?::uuid", String.class, novo))
                .isEqualTo("QR");
    }

    @Test
    void corrigirNaMesmaAulaRecalculaPontualidade() {
        String id = qr(aluno).texto("checkinId");
        agora(19, 30);
        var r = corrigir(id, SEGUNDA.toString(), "19:20", null, "chegou mais tarde");
        assertThat(r.status()).as(r.corpo().toString()).isEqualTo(201);
        assertThat(r.texto("pontualidade")).isEqualTo("ATRASADO");
        assertThat(checkinsAtivos(aluno)).isEqualTo(1);
    }

    @Test
    void corrigirNaoReavaliaMatricula() {
        String id = qr(aluno).texto("checkinId");
        su.jdbc().update("update matricula set status = 'VENCIDA' where aluno_id = ?", aluno);
        assertThat(corrigir(id, SEGUNDA.toString(), "19:02", null, "ajuste").status()).isEqualTo(201);
    }

    @Test
    void corrigirForaDoPrazo409() {
        var antigo = SEGUNDA.minusDays(31);
        String id = DadosDeTeste.checkin(su.jdbc(), admin.academiaId(), aluno, null, antigo, of(10, 0)).toString();

        var r = corrigir(id, SEGUNDA.toString(), "10:00", null, "ajuste");
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.texto("codigo")).isEqualTo("CHECKIN_FORA_DO_PRAZO");
        assertThat(excluir(id, "ajuste").texto("codigo")).isEqualTo("CHECKIN_FORA_DO_PRAZO");
    }

    @Test
    void corrigirParaAulaOndeJaTem409() {
        UUID b = horario(of(20, 0), of(21, 0), "No-Gi");
        String primeiro = qr(aluno).texto("checkinId");
        agora(20, 5);
        qr(token(aluno), b);

        var r = corrigir(primeiro, SEGUNDA.toString(), "20:05", b, "duplicado");
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.texto("codigo")).isEqualTo("CHECKIN_DUPLICADO");
        assertThat(status(primeiro)).isEqualTo("ATIVO");
    }

    @Test
    void corrigirParaOFuturo400() {
        String id = qr(aluno).texto("checkinId");
        assertThat(corrigir(id, SEGUNDA.toString(), "21:00", null, "x").status()).isEqualTo(400);
    }

    @Test
    void eventosEAuditoria() {
        String id = qr(aluno).texto("checkinId");
        String novo = corrigir(id, SEGUNDA.toString(), "19:02", null, "ajuste").texto("checkinId");
        excluir(novo, "não veio");

        assertThat(su.jdbc().queryForObject(
                "select payload->'dados'->>'checkinNovoId' from outbox_evento where tipo = 'checkin.corrigido' and payload->'dados'->>'checkinAntigoId' = ?",
                String.class, id)).isEqualTo(novo);
        assertThat(su.jdbc().queryForObject(
                "select payload->'dados'->>'motivo' from outbox_evento where tipo = 'checkin.excluido' and payload->'dados'->>'checkinId' = ?",
                String.class, novo)).isEqualTo("não veio");
        assertThat(su.jdbc().queryForObject(
                "select count(*) from auditoria where acao = 'CHECKIN_CORRIGIDO' and entidade_id = ?::uuid", Integer.class, id))
                .isEqualTo(1);
        assertThat(su.jdbc().queryForObject(
                "select count(*) from auditoria where acao = 'CHECKIN_EXCLUIDO' and entidade_id = ?::uuid", Integer.class, novo))
                .isEqualTo(1);
    }

    @Test
    void isolamento() {
        String id = qr(aluno).texto("checkinId");
        ClienteHttp b = comoAdmin(http, novoAdministrador(http));
        assertThat(b.post("/api/v1/checkins/" + id + "/excluir", Map.of("motivo", "x")).status()).isEqualTo(404);
        assertThat(b.post("/api/v1/checkins/" + id + "/corrigir",
                Map.of("data", SEGUNDA.toString(), "hora", "19:00", "motivo", "x")).status()).isEqualTo(404);
    }
}
