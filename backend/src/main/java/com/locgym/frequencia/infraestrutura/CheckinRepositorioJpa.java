package com.locgym.frequencia.infraestrutura;

import com.locgym.compartilhado.erros.RegraVioladaException;
import com.locgym.compartilhado.paginacao.Pagina;
import com.locgym.frequencia.dominio.Checkin;
import com.locgym.frequencia.dominio.CheckinRepositorio;
import com.locgym.frequencia.dominio.Origem;
import com.locgym.frequencia.dominio.Pontualidade;
import com.locgym.frequencia.dominio.StatusCheckin;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
class CheckinRepositorioJpa implements CheckinRepositorio {

    private static final String PREFIXO_INDICE_UNICO = "ux_checkin_";

    private final CheckinJpaDados dados;
    private final NamedParameterJdbcTemplate jdbc;

    CheckinRepositorioJpa(CheckinJpaDados dados, NamedParameterJdbcTemplate jdbc) {
        this.dados = dados;
        this.jdbc = jdbc;
    }

    @Override
    public void inserir(Checkin checkin) {
        try {
            dados.saveAndFlush(CheckinJpa.novo(checkin));
        } catch (DataIntegrityViolationException e) {
            if (String.valueOf(e.getMostSpecificCause().getMessage()).contains(PREFIXO_INDICE_UNICO)) {
                throw new RegraVioladaException("CHECKIN_DUPLICADO",
                        checkin.livre() ? "Check-in já registrado hoje" : "Check-in já registrado nesta aula");
            }
            throw e;
        }
    }

    @Override
    public Optional<Checkin> porId(UUID id) {
        return dados.findById(id).map(CheckinJpa::paraDominio);
    }

    /** Flush imediato: conflito de versão vira erro aqui, dentro da transação. */
    @Override
    public void salvar(Checkin checkin) {
        CheckinJpa e = dados.findById(checkin.id())
                .orElseThrow(() -> new IllegalStateException("Check-in inexistente: " + checkin.id()));
        e.copiar(checkin);
        dados.saveAndFlush(e);
    }

    /** Listagem por JDBC; o RLS restringe à academia do contexto. */
    @Override
    public Pagina<Checkin> listar(FiltroCheckins f, int pagina, int tamanho) {
        var params = new MapSqlParameterSource()
                .addValue("aluno", f.alunoId()).addValue("horario", f.horarioId())
                .addValue("de", f.de()).addValue("ate", f.ate())
                .addValue("status", f.status() == null ? null : f.status().name())
                .addValue("limite", tamanho).addValue("deslocamento", (long) pagina * tamanho);
        String filtro = """
                 where (cast(:aluno as uuid) is null or c.aluno_id = :aluno)
                   and (cast(:horario as uuid) is null or c.aula_id in (select a.id from aula a where a.horario_id = :horario))
                   and (cast(:de as date) is null or c.data >= :de)
                   and (cast(:ate as date) is null or c.data <= :ate)
                   and (cast(:status as varchar) is null or c.status = :status)
                """;
        Long total = jdbc.queryForObject("select count(*) from checkin c" + filtro, params, Long.class);
        List<Checkin> itens = jdbc.query("select c.* from checkin c" + filtro
                + " order by c.data desc, c.hora desc, c.registrado_em desc, c.id limit :limite offset :deslocamento",
                params, CheckinRepositorioJpa::linha);
        return new Pagina<>(itens, pagina, tamanho, total == null ? 0 : total);
    }

    private static Checkin linha(ResultSet rs, int i) throws SQLException {
        String pontualidade = rs.getString("pontualidade");
        return new Checkin(rs.getObject("id", UUID.class), rs.getObject("aluno_id", UUID.class),
                rs.getObject("aula_id", UUID.class), rs.getObject("data", LocalDate.class),
                rs.getObject("hora", LocalTime.class), rs.getTimestamp("registrado_em").toInstant(),
                Origem.valueOf(rs.getString("origem")), pontualidade == null ? null : Pontualidade.valueOf(pontualidade),
                StatusCheckin.valueOf(rs.getString("status")), rs.getString("motivo"),
                rs.getObject("substituido_por", UUID.class), rs.getObject("registrado_por", UUID.class));
    }
}
