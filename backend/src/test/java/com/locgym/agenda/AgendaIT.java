package com.locgym.agenda;

import static com.locgym.apoio.Cenarios.novoAdministrador;
import static java.time.LocalTime.of;
import static org.assertj.core.api.Assertions.assertThat;

import com.locgym.TestcontainersConfig;
import com.locgym.TestcontainersConfig.BancoSuperusuario;
import com.locgym.apoio.Cenarios.Admin;
import com.locgym.apoio.ClienteHttp;
import com.locgym.compartilhado.tenancy.ContextoTenant;
import com.locgym.compartilhado.tenancy.DadosDeTeste;
import com.locgym.compartilhado.tenancy.Perfil;
import com.locgym.compartilhado.tenancy.TenantContext;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"locgym.outbox.relay-habilitado=false", "locgym.rotina-diaria.habilitada=false"})
@Import(TestcontainersConfig.class)
class AgendaIT {

    static final LocalDate SEGUNDA = LocalDate.of(2026, 11, 23);

    @LocalServerPort int porta;
    @Autowired JsonMapper json;
    @Autowired BancoSuperusuario su;
    @Autowired Agenda agenda;
    @Autowired TransactionTemplate tx;

    Admin admin;
    UUID professor;

    @BeforeEach
    void preparar() {
        admin = novoAdministrador(new ClienteHttp(porta, json));
        professor = DadosDeTeste.professor(su.jdbc(), admin.academiaId(), "ATIVO");
    }

    <T> T naAcademia(Supplier<T> acao) {
        TenantContext.definir(new ContextoTenant(admin.academiaId(), Perfil.ADMIN, true, admin.usuarioId()));
        try {
            return tx.execute(s -> acao.get());
        } finally {
            TenantContext.limpar();
        }
    }

    UUID horario(int dia, LocalTime inicio, LocalTime fim, String tipo) {
        return DadosDeTeste.horario(su.jdbc(), admin.academiaId(), professor, dia, inicio, fim, tipo);
    }

    List<AulaAberta> abertas(LocalDate data, LocalTime hora) {
        return naAcademia(() -> agenda.abertas(data, hora));
    }

    @Test
    void abertasNaJanela() {
        UUID h = horario(1, of(19, 0), of(20, 0), "Gi");

        assertThat(abertas(SEGUNDA, of(18, 29))).isEmpty();
        var abertas = abertas(SEGUNDA, of(18, 30));
        assertThat(abertas).hasSize(1);
        var a = abertas.get(0);
        assertThat(a.horarioId()).isEqualTo(h);
        assertThat(a.aulaId()).isNull();
        assertThat(a.tipo()).isEqualTo("Gi");
        assertThat(a.professorId()).isEqualTo(professor);
        assertThat(a.professorNome()).startsWith("Prof ");
        assertThat(a.inicio()).isEqualTo(of(19, 0));
        assertThat(a.fim()).isEqualTo(of(20, 0));
        assertThat(abertas(SEGUNDA, of(20, 0))).isEmpty();
    }

    @Test
    void outroDiaDaSemanaNaoAbre() {
        horario(2, of(19, 0), of(20, 0), "Gi");
        assertThat(abertas(SEGUNDA, of(19, 0))).isEmpty();
    }

    @Test
    void inativoNaoAbre() {
        UUID h = horario(1, of(19, 0), of(20, 0), "Gi");
        su.jdbc().update("update horario set status = 'INATIVO' where id = ?", h);
        assertThat(abertas(SEGUNDA, of(19, 0))).isEmpty();
    }

    @Test
    void duasSobrepostasAmbasAbertas() {
        horario(1, of(20, 0), of(21, 0), "Gi");
        horario(1, of(19, 0), of(20, 0), "Gi");
        var abertas = abertas(SEGUNDA, of(19, 45));
        assertThat(abertas).extracting(AulaAberta::inicio).containsExactly(of(19, 0), of(20, 0));
    }

    @Test
    void garantirAulaIdempotente() {
        UUID h = horario(1, of(19, 0), of(20, 0), "Gi");
        var primeira = naAcademia(() -> agenda.garantirAula(h, SEGUNDA));
        var segunda = naAcademia(() -> agenda.garantirAula(h, SEGUNDA));

        assertThat(segunda.id()).isEqualTo(primeira.id());
        assertThat(primeira.horarioId()).isEqualTo(h);
        assertThat(primeira.data()).isEqualTo(SEGUNDA);
        assertThat(primeira.inicio()).isEqualTo(of(19, 0));
        assertThat(primeira.professorNome()).startsWith("Prof ");
        assertThat(su.jdbc().queryForObject("select count(*) from aula where horario_id = ?", Integer.class, h))
                .isEqualTo(1);
        assertThat(naAcademia(() -> agenda.aulas(List.of(primeira.id()))).get(primeira.id()).tipo()).isEqualTo("Gi");
    }

    @Test
    void garantirAulaDeHorarioInexistente404() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> naAcademia(() -> agenda.garantirAula(UUID.randomUUID(), SEGUNDA)))
                .isInstanceOf(com.locgym.compartilhado.erros.NaoEncontradoException.class);
    }

    @Test
    void aulaCriadaContinuaAbertaAposAlterarHorario() {
        UUID h = horario(1, of(19, 0), of(20, 0), "Gi");
        var aula = naAcademia(() -> agenda.garantirAula(h, SEGUNDA));
        su.jdbc().update("update horario set inicio = '20:00', fim = '21:00', status = 'INATIVO' where id = ?", h);

        var abertas = abertas(SEGUNDA, of(19, 15));
        assertThat(abertas).hasSize(1);
        assertThat(abertas.get(0).aulaId()).isEqualTo(aula.id());
        assertThat(abertas.get(0).inicio()).isEqualTo(of(19, 0));
        // e o horário alterado não abre de novo no mesmo dia (já tem aula)
        su.jdbc().update("update horario set status = 'ATIVO' where id = ?", h);
        assertThat(abertas(SEGUNDA, of(20, 15))).isEmpty();
    }

    @Test
    void garantirAulaConcorrente() throws Exception {
        UUID h = horario(1, of(19, 0), of(20, 0), "Gi");
        Callable<UUID> tarefa = () -> naAcademia(() -> agenda.garantirAula(h, SEGUNDA)).id();
        try (var exec = Executors.newFixedThreadPool(2)) {
            var a = exec.submit(tarefa);
            var b = exec.submit(tarefa);
            assertThat(a.get()).isEqualTo(b.get());
        }
        assertThat(su.jdbc().queryForObject("select count(*) from aula where horario_id = ?", Integer.class, h))
                .isEqualTo(1);
    }

    @Test
    void isolamento() {
        UUID h = horario(1, of(19, 0), of(20, 0), "Gi");
        Admin outro = novoAdministrador(new ClienteHttp(porta, json));
        TenantContext.definir(new ContextoTenant(outro.academiaId(), Perfil.ADMIN, true, outro.usuarioId()));
        try {
            assertThat(tx.<List<AulaAberta>>execute(s -> agenda.abertas(SEGUNDA, of(19, 0)))).isEmpty();
        } finally {
            TenantContext.limpar();
        }
        assertThat(abertas(SEGUNDA, of(19, 0))).extracting(AulaAberta::horarioId).containsExactly(h);
    }
}
