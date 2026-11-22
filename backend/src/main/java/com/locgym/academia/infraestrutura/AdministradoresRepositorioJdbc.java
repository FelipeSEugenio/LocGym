package com.locgym.academia.infraestrutura;

import com.locgym.academia.dominio.Administrador;
import com.locgym.academia.dominio.Portas.AdministradoresRepositorio;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class AdministradoresRepositorioJdbc implements AdministradoresRepositorio {

    private final JdbcTemplate jdbc;

    AdministradoresRepositorioJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Administrador> daAcademia(UUID academiaId) {
        return jdbc.query("""
                select aa.usuario_id, aa.nome, u.email
                  from administrador_academia aa
                  join usuario u on u.id = aa.usuario_id
                 where aa.academia_id = ?
                 order by lower(aa.nome)
                """, (rs, i) -> new Administrador(rs.getObject("usuario_id", UUID.class), rs.getString("nome"),
                rs.getString("email")), academiaId);
    }

    @Override
    public boolean existe(UUID usuarioId, UUID academiaId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from administrador_academia where usuario_id = ? and academia_id = ?)",
                Boolean.class, usuarioId, academiaId));
    }

    @Override
    public int contar(UUID academiaId) {
        return jdbc.queryForObject("select count(*) from administrador_academia where academia_id = ?",
                Integer.class, academiaId);
    }

    @Override
    public void adicionar(UUID usuarioId, UUID academiaId, String nome) {
        jdbc.update("insert into administrador_academia (usuario_id, academia_id, nome) values (?, ?, ?)",
                usuarioId, academiaId, nome);
    }

    @Override
    public void remover(UUID usuarioId, UUID academiaId) {
        jdbc.update("delete from administrador_academia where usuario_id = ? and academia_id = ?", usuarioId, academiaId);
    }
}
