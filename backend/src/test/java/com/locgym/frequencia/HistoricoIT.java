package com.locgym.frequencia;

import static com.locgym.apoio.Cenarios.comoAdmin;
import static com.locgym.apoio.Cenarios.novoAdministrador;
import static java.time.LocalTime.of;
import static org.assertj.core.api.Assertions.assertThat;

import com.locgym.TestcontainersConfig;
import com.locgym.apoio.CalendarioDeTeste;
import com.locgym.compartilhado.tenancy.DadosDeTeste;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"locgym.outbox.relay-habilitado=false", "locgym.rotina-diaria.habilitada=false"})
@Import({TestcontainersConfig.class, CalendarioDeTeste.Config.class})
class HistoricoIT extends BaseFrequenciaIT {

    UUID checkin(UUID alunoId, UUID aula, LocalDate data, int hora) {
        return DadosDeTeste.checkin(su.jdbc(), admin.academiaId(), alunoId, aula, data, of(hora, 0));
    }

    @Test
    void padraoUltimos30DiasAtivos() {
        checkin(aluno, null, SEGUNDA.minusDays(31), 10);
        UUID excluido = checkin(aluno, null, SEGUNDA.minusDays(2), 10);
        su.jdbc().update("update checkin set status = 'EXCLUIDO', motivo = 'x' where id = ?", excluido);
        UUID valido = checkin(aluno, null, SEGUNDA.minusDays(30), 10);

        var r = ctx.get("/api/v1/checkins").corpo();

        assertThat(r.get("total").asInt()).isEqualTo(1);
        assertThat(r.get("itens").get(0).get("id").asString()).isEqualTo(valido.toString());
        assertThat(ctx.get("/api/v1/checkins?status=EXCLUIDO").corpo().get("total").asInt()).isEqualTo(1);
        assertThat(ctx.get("/api/v1/checkins?de=" + SEGUNDA.minusDays(40)).corpo().get("total").asInt()).isEqualTo(2);
    }

    @Test
    void filtrosPorAlunoEHorario() {
        UUID outro = alunoComMatricula("ATIVA", LocalDate.of(2026, 11, 1));
        UUID aula = DadosDeTeste.aula(su.jdbc(), horario, SEGUNDA);
        checkin(aluno, aula, SEGUNDA, 19);
        checkin(outro, aula, SEGUNDA, 19);
        checkin(outro, null, SEGUNDA.minusDays(1), 10);

        assertThat(ctx.get("/api/v1/checkins?alunoId=" + outro).corpo().get("total").asInt()).isEqualTo(2);
        assertThat(ctx.get("/api/v1/checkins?horarioId=" + horario).corpo().get("total").asInt()).isEqualTo(2);
    }

    @Test
    void ordemMaisRecentePrimeiroComDadosDaAula() {
        UUID aula = DadosDeTeste.aula(su.jdbc(), horario, SEGUNDA);
        checkin(aluno, null, SEGUNDA.minusDays(1), 10);
        UUID recente = checkin(aluno, aula, SEGUNDA, 19);

        var itens = ctx.get("/api/v1/checkins").corpo().get("itens");

        var primeiro = itens.get(0);
        assertThat(primeiro.get("id").asString()).isEqualTo(recente.toString());
        assertThat(primeiro.get("alunoNome").asString()).isNotBlank();
        assertThat(primeiro.get("data").asString()).isEqualTo("2026-11-23");
        assertThat(primeiro.get("hora").asString()).isEqualTo("19:00");
        assertThat(primeiro.get("origem").asString()).isEqualTo("QR");
        assertThat(primeiro.get("pontualidade").asString()).isEqualTo("PONTUAL");
        assertThat(primeiro.get("status").asString()).isEqualTo("ATIVO");
        assertThat(primeiro.get("aula").get("tipo").asString()).isEqualTo("Gi");
        assertThat(primeiro.get("aula").get("horarioId").asString()).isEqualTo(horario.toString());
        assertThat(primeiro.get("aula").get("professorNome").asString()).startsWith("Prof ");
        assertThat(itens.get(1).get("aula").isNull()).isTrue();
    }

    @Test
    void paginacao() {
        for (int i = 0; i < 3; i++) {
            checkin(aluno, null, SEGUNDA.minusDays(i), 10);
        }
        var r = ctx.get("/api/v1/checkins?pagina=1&tamanho=2").corpo();
        assertThat(r.get("total").asInt()).isEqualTo(3);
        assertThat(r.get("itens").size()).isEqualTo(1);
    }

    @Test
    void isolamento() {
        checkin(aluno, null, SEGUNDA, 10);
        var b = comoAdmin(http, novoAdministrador(http));
        assertThat(b.get("/api/v1/checkins").corpo().get("total").asInt()).isZero();
    }
}
