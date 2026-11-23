package com.locgym.matricula;

import static com.locgym.apoio.Cenarios.comoAdmin;
import static com.locgym.apoio.Cenarios.novoAdministrador;
import static java.time.LocalDate.of;
import static org.assertj.core.api.Assertions.assertThat;

import com.locgym.TestcontainersConfig;
import com.locgym.TestcontainersConfig.BancoSuperusuario;
import com.locgym.apoio.CalendarioDeTeste;
import com.locgym.apoio.Cenarios.Admin;
import com.locgym.apoio.ClienteHttp;
import com.locgym.compartilhado.tenancy.DadosDeTeste;
import java.time.LocalDate;
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
@Import({TestcontainersConfig.class, CalendarioDeTeste.Config.class})
class InadimplenciaIT {

    static final LocalDate HOJE = of(2026, 11, 30);

    @LocalServerPort int porta;
    @Autowired JsonMapper json;
    @Autowired BancoSuperusuario su;
    @Autowired CalendarioDeTeste calendario;

    ClienteHttp http;
    Admin admin;
    ClienteHttp ctx;
    UUID plano;

    @BeforeEach
    void preparar() {
        calendario.definir(HOJE);
        http = new ClienteHttp(porta, json);
        admin = novoAdministrador(http);
        ctx = comoAdmin(http, admin);
        plano = DadosDeTeste.plano(su.jdbc(), admin.academiaId(), "Mensal");
    }

    /** Aluno com matrícula e as cobranças atrasadas informadas (R$ 150,00 cada). */
    UUID devedor(String nome, LocalDate... atrasadas) {
        UUID usuario = DadosDeTeste.usuario(su.jdbc(), "devedor-" + UUID.randomUUID() + "@teste.com");
        UUID aluno = DadosDeTeste.aluno(su.jdbc(), usuario, admin.academiaId(), "ATIVO");
        su.jdbc().update("update aluno set nome = ? where id = ?", nome, aluno);
        UUID matricula = DadosDeTeste.matricula(su.jdbc(), admin.academiaId(), aluno, plano, of(2026, 6, 1));
        for (LocalDate inicio : atrasadas) {
            UUID c = DadosDeTeste.cobranca(su.jdbc(), admin.academiaId(), matricula, aluno, inicio);
            su.jdbc().update("update pagamento set status = 'ATRASADO' where id = ?", c);
        }
        return aluno;
    }

    @Test
    void listaAtrasadosOrdenados() {
        UUID recente = devedor("Bruno", of(2026, 11, 25));
        UUID antigo = devedor("Carla", of(2026, 10, 31), of(2026, 11, 1));
        devedor("Em dia");

        var r = ctx.get("/api/v1/inadimplencia").corpo();

        assertThat(r.get("total").asInt()).isEqualTo(2);
        var primeiro = r.get("itens").get(0);
        assertThat(primeiro.get("alunoId").asString()).isEqualTo(antigo.toString());
        assertThat(primeiro.get("nome").asString()).isEqualTo("Carla");
        assertThat(primeiro.get("cobrancasAtrasadas").asInt()).isEqualTo(2);
        assertThat(primeiro.get("totalEmAtraso").decimalValue()).isEqualByComparingTo("300.00");
        assertThat(primeiro.get("vencimentoMaisAntigo").asString()).isEqualTo("2026-10-31");
        assertThat(primeiro.get("diasDeAtraso").asInt()).isEqualTo(30);
        assertThat(primeiro.get("statusMatricula").asString()).isEqualTo("ATIVA");
        assertThat(primeiro.get("matriculaId").asString()).isNotBlank();
        var segundo = r.get("itens").get(1);
        assertThat(segundo.get("alunoId").asString()).isEqualTo(recente.toString());
        assertThat(segundo.get("diasDeAtraso").asInt()).isEqualTo(5);
    }

    @Test
    void incluiMatriculaCancelada() {
        UUID aluno = devedor("Diego", of(2026, 11, 1));
        su.jdbc().update("update matricula set status = 'CANCELADA', motivo = 'saiu' where aluno_id = ?", aluno);

        var item = ctx.get("/api/v1/inadimplencia").corpo().get("itens").get(0);

        assertThat(item.get("alunoId").asString()).isEqualTo(aluno.toString());
        assertThat(item.get("statusMatricula").asString()).isEqualTo("CANCELADA");
    }

    @Test
    void buscaPorNome() {
        devedor("Fernanda Lima", of(2026, 11, 1));
        devedor("Gustavo", of(2026, 11, 1));

        var r = ctx.get("/api/v1/inadimplencia?busca=fernanda").corpo();

        assertThat(r.get("total").asInt()).isEqualTo(1);
        assertThat(r.get("itens").get(0).get("nome").asString()).isEqualTo("Fernanda Lima");
    }

    @Test
    void isolamento() {
        devedor("Helena", of(2026, 11, 1));
        ClienteHttp b = comoAdmin(http, novoAdministrador(http));

        assertThat(b.get("/api/v1/inadimplencia").corpo().get("total").asInt()).isZero();
    }
}
