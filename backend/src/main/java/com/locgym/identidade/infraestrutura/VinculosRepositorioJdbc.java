package com.locgym.identidade.infraestrutura;

import com.locgym.compartilhado.tenancy.Perfil;
import com.locgym.identidade.dominio.Portas.VinculosRepositorio;
import com.locgym.identidade.dominio.Vinculo;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Lê os vínculos pela função SECURITY DEFINER — a única leitura que atravessa academias. */
@Repository
class VinculosRepositorioJdbc implements VinculosRepositorio {

    private final JdbcTemplate jdbc;

    VinculosRepositorioJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Vinculo> doUsuario(UUID usuarioId) {
        return jdbc.query("""
                select academia_id, academia_nome, academia_status, perfil
                  from vinculos_do_usuario(?)
                 order by academia_nome, perfil
                """,
                (rs, i) -> new Vinculo(
                        rs.getObject("academia_id", UUID.class),
                        rs.getString("academia_nome"),
                        rs.getString("academia_status"),
                        Perfil.valueOf(rs.getString("perfil"))),
                usuarioId);
    }
}
