package com.locgym.agenda.infraestrutura;

import com.locgym.agenda.dominio.AulaRepositorio;
import com.locgym.agenda.dominio.Horario;
import com.locgym.compartilhado.tenancy.TenantContext;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Aulas por JDBC: criação idempotente com {@code on conflict}; o RLS filtra pela academia do contexto. */
@Repository
class AulaJdbc implements AulaRepositorio {

    private final NamedParameterJdbcTemplate jdbc;

    AulaJdbc(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Aula garantir(Horario h, LocalDate data) {
        jdbc.update("""
                insert into aula (id, academia_id, horario_id, data, inicio, fim, professor_id, tipo)
                values (:id, :academia, :horario, :data, :inicio, :fim, :professor, :tipo)
                on conflict on constraint ux_aula_horario_data do nothing
                """, Map.of("id", UUID.randomUUID(), "academia", TenantContext.exigir().academiaId(),
                "horario", h.id(), "data", data, "inicio", h.inicio(), "fim", h.fim(),
                "professor", h.professorId(), "tipo", h.tipo()));
        return jdbc.queryForObject("select * from aula where horario_id = :horario and data = :data",
                Map.of("horario", h.id(), "data", data), AulaJdbc::linha);
    }

    @Override
    public List<Aula> doDia(LocalDate data) {
        return jdbc.query("select * from aula where data = :data", Map.of("data", data), AulaJdbc::linha);
    }

    @Override
    public boolean horarioTemAulas(UUID horarioId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from aula where horario_id = :horario)", Map.of("horario", horarioId),
                Boolean.class));
    }

    @Override
    public Map<UUID, Aula> porIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return jdbc.query("select * from aula where id in (:ids)", Map.of("ids", ids), AulaJdbc::linha).stream()
                .collect(Collectors.toMap(Aula::id, Function.identity()));
    }

    private static Aula linha(ResultSet rs, int i) throws SQLException {
        return new Aula(rs.getObject("id", UUID.class), rs.getObject("horario_id", UUID.class),
                rs.getObject("data", LocalDate.class), rs.getObject("inicio", LocalTime.class),
                rs.getObject("fim", LocalTime.class), rs.getObject("professor_id", UUID.class), rs.getString("tipo"));
    }
}
