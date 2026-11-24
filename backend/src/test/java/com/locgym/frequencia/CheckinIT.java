package com.locgym.frequencia;

import static com.locgym.apoio.Cenarios.comoAdmin;
import static com.locgym.apoio.Cenarios.comoAluno;
import static com.locgym.apoio.Cenarios.cpfValido;
import static com.locgym.apoio.Cenarios.dadosAluno;
import static com.locgym.apoio.Cenarios.emailUnico;
import static com.locgym.apoio.Cenarios.novoAdministrador;
import static java.time.LocalTime.of;
import static org.assertj.core.api.Assertions.assertThat;

import com.locgym.TestcontainersConfig;
import com.locgym.apoio.CalendarioDeTeste;
import com.locgym.apoio.ClienteHttp;
import com.locgym.compartilhado.tenancy.DadosDeTeste;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"locgym.outbox.relay-habilitado=false", "locgym.rotina-diaria.habilitada=false"})
@Import({TestcontainersConfig.class, CalendarioDeTeste.Config.class})
class CheckinIT extends BaseFrequenciaIT {

    @Test
    void qrPontual() {
        var r = qr(aluno);

        assertThat(r.status()).as(r.corpo().toString()).isEqualTo(201);
        assertThat(r.texto("pontualidade")).isEqualTo("PONTUAL");
        assertThat(r.corpo().get("livre").asBoolean()).isFalse();
        assertThat(r.corpo().get("aluno").get("id").asString()).isEqualTo(aluno.toString());
        assertThat(r.corpo().get("aula").get("horarioId").asString()).isEqualTo(horario.toString());
        assertThat(r.corpo().get("aula").get("inicio").asString()).isEqualTo("19:00");
        assertThat(r.corpo().get("aula").get("tipo").asString()).isEqualTo("Gi");
        assertThat(r.corpo().get("alerta").isNull()).isTrue();
        assertThat(su.jdbc().queryForObject("select count(*) from aula where horario_id = ?", Integer.class, horario))
                .isEqualTo(1);
        assertThat(su.jdbc().queryForObject("select hora::text from checkin where aluno_id = ?", String.class, aluno))
                .isEqualTo("19:05:00");
    }

    @Test
    void alunoMostraQrDoPortal() {
        String email = emailUnico("portal");
        String id = ctx.post("/api/v1/alunos", dadosAluno("Diego Lima", email, cpfValido())).texto("id");
        DadosDeTeste.matricula(su.jdbc(), admin.academiaId(), UUID.fromString(id), plano, LocalDate.of(2026, 11, 1));
        String token = comoAluno(http, email, admin.academiaId()).get("/api/v1/eu/qr-checkin").texto("token");

        var r = qr(token, null);
        assertThat(r.status()).as(r.corpo().toString()).isEqualTo(201);
        assertThat(r.corpo().get("aluno").get("nome").asString()).isEqualTo("Diego Lima");
    }

    @Test
    void qrAtrasado() {
        agora(19, 11);
        assertThat(qr(aluno).texto("pontualidade")).isEqualTo("ATRASADO");
    }

    @Test
    void segundoAlunoReaproveitaAula() {
        UUID outro = alunoComMatricula("ATIVA", LocalDate.of(2026, 11, 1));
        String a = qr(aluno).corpo().get("aula").get("horarioId").asString();
        qr(outro);
        assertThat(su.jdbc().queryForObject("select count(distinct aula_id) from checkin where aula_id is not null",
                Integer.class)).isGreaterThanOrEqualTo(1);
        assertThat(su.jdbc().queryForObject("select count(*) from aula where horario_id = ?::uuid", Integer.class, a))
                .isEqualTo(1);
    }

    @Test
    void semAulaAbertaCheckinLivre() {
        agora(15, 0);
        var r = qr(aluno);
        assertThat(r.status()).isEqualTo(201);
        assertThat(r.corpo().get("livre").asBoolean()).isTrue();
        assertThat(r.corpo().get("aula").isNull()).isTrue();
        assertThat(r.corpo().get("pontualidade").isNull()).isTrue();
    }

    @Test
    void livreDuplicadoNoDia409() {
        agora(15, 0);
        qr(aluno);
        agora(16, 0);
        var r = qr(aluno);
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.texto("codigo")).isEqualTo("CHECKIN_DUPLICADO");
    }

    @Test
    void duasAbertasSemEscolha409ComLista() {
        horario(of(19, 0), of(20, 0), "Kids");
        var r = qr(aluno);
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.texto("codigo")).isEqualTo("AULA_AMBIGUA");
        assertThat(r.corpo().get("aulas").size()).isEqualTo(2);
        assertThat(checkinsAtivos(aluno)).isZero();
    }

    @Test
    void comEscolha201() {
        UUID kids = horario(of(19, 0), of(20, 0), "Kids");
        var r = qr(token(aluno), kids);
        assertThat(r.status()).isEqualTo(201);
        assertThat(r.corpo().get("aula").get("tipo").asString()).isEqualTo("Kids");
    }

    @Test
    void horarioNaoAberto409() {
        UUID manha = horario(of(7, 0), of(8, 0), "Gi");
        var r = qr(token(aluno), manha);
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.texto("codigo")).isEqualTo("AULA_NAO_ABERTA");
    }

    @Test
    void qrDuplicadoNaMesmaAula409() {
        String t = token(aluno);
        assertThat(qr(t, null).status()).isEqualTo(201);
        var r = qr(t, null);
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.texto("codigo")).isEqualTo("CHECKIN_DUPLICADO");
    }

    @Test
    void qrConcorrenteUmSucessoUm409() throws Exception {
        String t = token(aluno);
        Callable<Integer> tarefa = () -> qr(t, null).status();
        var status = new ArrayList<Integer>();
        try (var exec = Executors.newFixedThreadPool(3)) {
            var futuros = java.util.List.of(exec.submit(tarefa), exec.submit(tarefa), exec.submit(tarefa));
            for (var f : futuros) {
                status.add(f.get());
            }
        }
        assertThat(status).containsExactlyInAnyOrder(201, 409, 409);
        assertThat(checkinsAtivos(aluno)).isEqualTo(1);
    }

    @Test
    void alunoInativo409() {
        su.jdbc().update("update aluno set status = 'INATIVO' where id = ?", aluno);
        assertThat(qr(aluno).texto("codigo")).isEqualTo("ALUNO_INATIVO");
    }

    @Test
    void semMatricula409() {
        su.jdbc().update("update matricula set status = 'CANCELADA', motivo = 'saiu' where aluno_id = ?", aluno);
        var r = qr(aluno);
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.texto("codigo")).isEqualTo("SEM_MATRICULA");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDENTE", "VENCIDA", "SUSPENSA"})
    void matriculaPendenteVencidaSuspensa409(String status) {
        UUID a = alunoComMatricula(status, LocalDate.of(2026, 11, 1));
        var r = qr(a);
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.texto("codigo")).isEqualTo("MATRICULA_NAO_ATIVA");
        assertThat(r.texto("status")).isEqualTo(status);
    }

    @Test
    void matriculaNaoIniciada409() {
        UUID a = alunoComMatricula("ATIVA", LocalDate.of(2026, 11, 25));
        var r = qr(a);
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.texto("codigo")).isEqualTo("MATRICULA_NAO_INICIADA");
        assertThat(r.texto("dataInicio")).isEqualTo("2026-11-25");
    }

    @Test
    void inadimplenteNaCarenciaPassaComAlerta() {
        UUID matricula = su.jdbc().queryForObject("select id from matricula where aluno_id = ?", UUID.class, aluno);
        UUID c = DadosDeTeste.cobranca(su.jdbc(), admin.academiaId(), matricula, aluno, LocalDate.of(2026, 11, 20));
        su.jdbc().update("update pagamento set status = 'ATRASADO' where id = ?", c);

        var r = qr(aluno);
        assertThat(r.status()).isEqualTo(201);
        assertThat(r.corpo().get("alerta").get("vencimentoMaisAntigo").asString()).isEqualTo("2026-11-20");
        assertThat(r.corpo().get("alerta").get("diasDeAtraso").asInt()).isEqualTo(3);
    }

    @Test
    void qrDeOutraAcademia400() {
        var outro = novoAdministrador(http);
        var r = qr(token(aluno, outro.academiaId(), Instant.now()), null);
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.texto("codigo")).isEqualTo("QR_DE_OUTRA_ACADEMIA");
    }

    @Test
    void qrExpirado400() {
        var r = qr(token(aluno, admin.academiaId(), Instant.now().minusSeconds(61)), null);
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.texto("codigo")).isEqualTo("QR_EXPIRADO");
        assertThat(qr("lixo-qualquer", null).texto("codigo")).isEqualTo("QR_INVALIDO");
    }

    @Test
    void checkinANoiteUsaDiaDeSaoPaulo() {
        UUID noite = horario(of(22, 0), of(23, 0), "No-Gi");
        agora(22, 10);
        var r = qr(aluno);
        assertThat(r.status()).isEqualTo(201);
        assertThat(r.corpo().get("aula").get("horarioId").asString()).isEqualTo(noite.toString());
        assertThat(su.jdbc().queryForObject("select data from checkin where aluno_id = ?", LocalDate.class, aluno))
                .isEqualTo(SEGUNDA);
    }

    @Test
    void academiaInativa409() {
        su.jdbc().update("update academia set status = 'INATIVA' where id = ?", admin.academiaId());
        var r = qr(aluno);
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.texto("codigo")).isEqualTo("ACADEMIA_INATIVA");
    }

    @Test
    void eventoNoOutbox() {
        String id = qr(aluno).texto("checkinId");
        var dados = su.jdbc().queryForMap("""
                select payload->'dados'->>'origem' as origem, payload->'dados'->>'pontualidade' as pontualidade,
                       payload->'dados'->>'alunoId' as aluno, payload::text as texto
                  from outbox_evento where tipo = 'checkin.registrado' and payload->'dados'->>'checkinId' = ?
                """, id);
        assertThat(dados.get("origem")).isEqualTo("QR");
        assertThat(dados.get("pontualidade")).isEqualTo("PONTUAL");
        assertThat(dados.get("aluno")).isEqualTo(aluno.toString());
        assertThat((String) dados.get("texto")).doesNotContain("nome");
    }

    @Test
    void aulasAbertasDaRecepcao() {
        horario(of(19, 0), of(20, 0), "Kids");
        var r = ctx.get("/api/v1/recepcao/aulas-abertas");
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.corpo().size()).isEqualTo(2);
        assertThat(r.corpo().get(0).get("inicio").asString()).isEqualTo("19:00");
        assertThat(r.corpo().get(0).get("professorNome").asString()).startsWith("Prof ");
        assertThat(ctx.get("/api/v1/recepcao/aulas-abertas?data=2026-11-23&hora=15:00").corpo().size()).isZero();
    }

    @Test
    void isolamento() {
        ClienteHttp b = comoAdmin(http, novoAdministrador(http));
        UUID alunoB = UUID.fromString(b.post("/api/v1/alunos", dadosAluno("Bia", emailUnico("b"), cpfValido())).texto("id"));

        var corpo = new java.util.LinkedHashMap<String, Object>();
        corpo.put("token", token(aluno));
        corpo.put("horarioId", horario.toString());
        // token da academia A lido na B
        assertThat(b.post("/api/v1/checkins/qr", corpo).texto("codigo")).isEqualTo("QR_DE_OUTRA_ACADEMIA");
        // horário da A não está aberto na B; aluno da B não existe na A
        assertThat(manual(alunoB, SEGUNDA, "19:05", null).status()).isEqualTo(404);
        assertThat(b.get("/api/v1/recepcao/aulas-abertas").corpo().size()).isZero();
    }
}
