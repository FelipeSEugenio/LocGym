package com.locgym.frequencia;

import static java.time.LocalTime.of;
import static org.assertj.core.api.Assertions.assertThat;

import com.locgym.TestcontainersConfig;
import com.locgym.apoio.CalendarioDeTeste;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"locgym.outbox.relay-habilitado=false", "locgym.rotina-diaria.habilitada=false"})
@Import({TestcontainersConfig.class, CalendarioDeTeste.Config.class})
class PresencaManualIT extends BaseFrequenciaIT {

    static final LocalDate SEXTA_ANTERIOR = LocalDate.of(2026, 11, 20);

    @Test
    void manualRetroativo() {
        UUID sexta = DadosDeTesteLocal.horarioNoDia(this, 5);
        var r = manual(aluno, SEXTA_ANTERIOR, "19:05", null);

        assertThat(r.status()).as(r.corpo().toString()).isEqualTo(201);
        assertThat(r.corpo().get("aula").get("horarioId").asString()).isEqualTo(sexta.toString());
        assertThat(su.jdbc().queryForObject("select origem from checkin where id = ?::uuid", String.class,
                r.texto("checkinId"))).isEqualTo("MANUAL");
        assertThat(su.jdbc().queryForObject("select data from aula where horario_id = ?", LocalDate.class, sexta))
                .isEqualTo(SEXTA_ANTERIOR);
        assertThat(su.jdbc().queryForObject("select registrado_por from checkin where id = ?::uuid", UUID.class,
                r.texto("checkinId"))).isEqualTo(admin.usuarioId());
        assertThat(su.jdbc().queryForObject(
                "select count(*) from auditoria where acao = 'PRESENCA_MANUAL_REGISTRADA' and entidade_id = ?::uuid",
                Integer.class, r.texto("checkinId"))).isEqualTo(1);
    }

    @Test
    void manualDataAntigaOuFutura400() {
        assertThat(manual(aluno, SEGUNDA.minusDays(31), "10:00", null).status()).isEqualTo(400);
        assertThat(manual(aluno, SEGUNDA, "23:00", null).status()).isEqualTo(400);
        UUID antigo = alunoComMatricula("ATIVA", LocalDate.of(2026, 10, 1));
        assertThat(manual(antigo, SEGUNDA.minusDays(30), "10:00", null).status()).isEqualTo(201);
    }

    @Test
    void manualSegueRegrasDeElegibilidade() {
        UUID vencido = alunoComMatricula("VENCIDA", LocalDate.of(2026, 11, 1));
        var r = manual(vencido, SEGUNDA, "19:05", null);
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.texto("codigo")).isEqualTo("MATRICULA_NAO_ATIVA");
    }

    @Test
    void manualEscolheAulaQuandoAmbigua() {
        UUID kids = horario(of(19, 0), of(20, 0), "Kids");
        assertThat(manual(aluno, SEGUNDA, "19:05", null).texto("codigo")).isEqualTo("AULA_AMBIGUA");
        var r = manual(aluno, SEGUNDA, "19:05", kids);
        assertThat(r.status()).isEqualTo(201);
        assertThat(r.corpo().get("aula").get("tipo").asString()).isEqualTo("Kids");
    }

    @Test
    void manualAlunoInexistente404() {
        assertThat(manual(UUID.randomUUID(), SEGUNDA, "19:05", null).status()).isEqualTo(404);
    }

    /** Horários em outros dias da semana. */
    static final class DadosDeTesteLocal {
        static UUID horarioNoDia(BaseFrequenciaIT it, int dia) {
            return com.locgym.compartilhado.tenancy.DadosDeTeste.horario(it.su.jdbc(), it.admin.academiaId(),
                    it.professor, dia, of(19, 0), of(20, 0), "Gi");
        }
    }
}
