package com.locgym.matricula.infraestrutura;

import com.locgym.compartilhado.paginacao.Pagina;
import com.locgym.matricula.dominio.ConsultaInadimplencia;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Consulta de leitura sobre {@code pagamento}, {@code aluno} e {@code matricula} — todas sob RLS da academia do
 * contexto. A matrícula mostrada é a não cancelada do aluno ou, se não houver, a mais recente.
 */
@Repository
class InadimplenciaJdbc implements ConsultaInadimplencia {

    private final JdbcTemplate jdbc;

    InadimplenciaJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Pagina<Linha> listar(String busca, int pagina, int tamanho) {
        String filtro = "";
        List<Object> args = new ArrayList<>();
        if (busca != null && !busca.isBlank()) {
            filtro = " and lower(a.nome) like ? escape '\\'";
            args.add("%" + MatriculaRepositorioJpa.escaparLike(busca.strip().toLowerCase(Locale.ROOT)) + "%");
        }
        String base = """
                from pagamento p join aluno a on a.id = p.aluno_id
                where p.status = 'ATRASADO'""" + filtro;
        Long total = jdbc.queryForObject("select count(distinct p.aluno_id) " + base, Long.class, args.toArray());
        List<Object> argsPagina = new ArrayList<>(args);
        argsPagina.add(tamanho);
        argsPagina.add((long) pagina * tamanho);
        List<Linha> itens = jdbc.query("""
                select d.*, m.id as matricula_id, m.status as status_matricula
                  from (select a.id as aluno_id, a.nome, a.cpf, sum(p.valor) as total, count(*) as quantidade,
                               min(p.vencimento) as mais_antigo
                        """ + base + """
                         group by a.id, a.nome, a.cpf) d
                  left join lateral (select id, status from matricula
                                      where aluno_id = d.aluno_id
                                      order by (status = 'CANCELADA'), criado_em desc limit 1) m on true
                 order by d.mais_antigo, lower(d.nome), d.aluno_id
                 limit ? offset ?
                """, (rs, i) -> new Linha(rs.getObject("aluno_id", UUID.class), rs.getString("nome"), rs.getString("cpf"),
                        rs.getBigDecimal("total"), rs.getInt("quantidade"), rs.getObject("mais_antigo", LocalDate.class),
                        rs.getObject("matricula_id", UUID.class), rs.getString("status_matricula")),
                argsPagina.toArray());
        return new Pagina<>(itens, pagina, tamanho, total == null ? 0 : total);
    }
}
