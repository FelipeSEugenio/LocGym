package com.locgym.matricula.infraestrutura;

import com.locgym.compartilhado.erros.RegraVioladaException;
import com.locgym.compartilhado.paginacao.Pagina;
import com.locgym.compartilhado.tenancy.TenantContext;
import com.locgym.matricula.dominio.Matricula;
import com.locgym.matricula.dominio.MatriculaListada;
import com.locgym.matricula.dominio.MatriculaRepositorio;
import com.locgym.matricula.dominio.Periodo;
import com.locgym.matricula.dominio.StatusMatricula;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Matrícula via JPA (@TenantId); períodos e listagem via SQL (a tabela de períodos não tem {@code atualizado_em};
 * a listagem junta aluno e plano). Tudo sob RLS da academia do contexto.
 */
@Repository
class MatriculaRepositorioJpa implements MatriculaRepositorio {

    private static final String INDICE_UMA_POR_ALUNO = "ux_matricula_aluno_nao_cancelada";

    private final MatriculaJpaDados dados;
    private final JdbcTemplate jdbc;

    MatriculaRepositorioJpa(MatriculaJpaDados dados, JdbcTemplate jdbc) {
        this.dados = dados;
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Matricula> porId(UUID id) {
        return dados.findById(id).map(MatriculaJpa::paraDominio);
    }

    @Override
    public Optional<Matricula> naoCanceladaDoAluno(UUID alunoId) {
        return dados.findFirstByAlunoIdAndStatusNot(alunoId, StatusMatricula.CANCELADA).map(MatriculaJpa::paraDominio);
    }

    @Override
    public List<Matricula> porStatus(StatusMatricula status) {
        return dados.findByStatusOrderById(status).stream().map(MatriculaJpa::paraDominio).toList();
    }

    /** Duas matrículas simultâneas para o mesmo aluno: o índice único decide; a segunda vira 409. */
    @Override
    public void inserir(Matricula m) {
        try {
            dados.saveAndFlush(MatriculaJpa.nova(m));
        } catch (DataIntegrityViolationException e) {
            if (String.valueOf(e.getMostSpecificCause().getMessage()).contains(INDICE_UMA_POR_ALUNO)) {
                throw new RegraVioladaException("MATRICULA_JA_EXISTE", "Este aluno já possui matrícula");
            }
            throw e;
        }
    }

    @Override
    public void salvar(Matricula m) {
        MatriculaJpa e = dados.findById(m.id()).orElseThrow(() -> new IllegalStateException("Matrícula inexistente: " + m.id()));
        e.copiar(m);
        dados.saveAndFlush(e);
    }

    @Override
    public void registrarPeriodo(UUID matriculaId, Periodo p, UUID cobrancaId) {
        jdbc.update("""
                insert into matricula_periodo (id, academia_id, matricula_id, inicio, fim, cobranca_id)
                values (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), TenantContext.exigir().academiaId(), matriculaId, p.inicio(), p.fim(), cobrancaId);
    }

    @Override
    public void deslocarPeriodosDesde(UUID matriculaId, LocalDate desde, int dias) {
        if (dias == 0) {
            return;
        }
        jdbc.update("""
                update matricula_periodo
                   set inicio = case when inicio > ? then inicio + ? else inicio end,
                       fim = fim + ?
                 where matricula_id = ? and fim > ?
                """, desde, dias, dias, matriculaId, desde);
    }

    @Override
    public List<Periodo> periodos(UUID matriculaId) {
        return jdbc.query("select inicio, fim from matricula_periodo where matricula_id = ? order by inicio",
                (rs, i) -> new Periodo(rs.getObject("inicio", LocalDate.class), rs.getObject("fim", LocalDate.class)),
                matriculaId);
    }

    @Override
    public Pagina<MatriculaListada> listar(StatusMatricula status, String busca, int pagina, int tamanho) {
        StringBuilder where = new StringBuilder(" where 1 = 1");
        List<Object> args = new ArrayList<>();
        if (status != null) {
            where.append(" and m.status = ?");
            args.add(status.name());
        }
        if (busca != null && !busca.isBlank()) {
            where.append(" and lower(a.nome) like ? escape '\\'");
            args.add("%" + escaparLike(busca.strip().toLowerCase(Locale.ROOT)) + "%");
        }
        String de = " from matricula m join aluno a on a.id = m.aluno_id join plano p on p.id = m.plano_id";
        Long total = jdbc.queryForObject("select count(*)" + de + where, Long.class, args.toArray());
        List<Object> argsPagina = new ArrayList<>(args);
        argsPagina.add(tamanho);
        argsPagina.add((long) pagina * tamanho);
        List<MatriculaListada> itens = jdbc.query("""
                select m.id, m.aluno_id, a.nome as aluno_nome, m.plano_id, p.nome as plano_nome, m.valor,
                       m.periodicidade, m.data_inicio, m.vigente_ate, m.status
                """ + de + where + " order by lower(a.nome), m.id limit ? offset ?",
                (rs, i) -> new MatriculaListada(rs.getObject("id", UUID.class), rs.getObject("aluno_id", UUID.class),
                        rs.getString("aluno_nome"), rs.getObject("plano_id", UUID.class), rs.getString("plano_nome"),
                        rs.getBigDecimal("valor"), rs.getString("periodicidade"),
                        rs.getObject("data_inicio", LocalDate.class), rs.getObject("vigente_ate", LocalDate.class),
                        StatusMatricula.valueOf(rs.getString("status"))),
                argsPagina.toArray());
        return new Pagina<>(itens, pagina, tamanho, total == null ? 0 : total);
    }

    /** {@code %} e {@code _} digitados pelo usuário são literais, não curingas. */
    static String escaparLike(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
