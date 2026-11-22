package com.locgym.academia.infraestrutura;

import com.locgym.academia.dominio.Academia;
import com.locgym.academia.dominio.DadosDaAcademia;
import com.locgym.academia.dominio.Portas.AcademiaRepositorio;
import com.locgym.academia.dominio.StatusAcademia;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** {@code academia} é tabela global (sem RLS): o acesso é sempre pela academia do contexto ou pelo criador. */
@Repository
class AcademiaRepositorioJdbc implements AcademiaRepositorio {

    private final JdbcTemplate jdbc;

    AcademiaRepositorioJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Academia> porId(UUID id) {
        return jdbc.query("select * from academia where id = ?", (rs, i) -> new Academia(
                rs.getObject("id", UUID.class),
                new DadosDaAcademia(rs.getString("nome"), rs.getString("cnpj"), rs.getString("endereco"),
                        rs.getString("cidade"), rs.getString("estado"), rs.getString("cep"),
                        rs.getString("telefone"), rs.getString("email")),
                StatusAcademia.valueOf(rs.getString("status"))), id).stream().findFirst();
    }

    @Override
    public void bloquear(UUID id) {
        jdbc.query("select id from academia where id = ? for update", rs -> {}, id);
    }

    @Override
    public boolean cnpjEmUso(String cnpj, UUID excetoAcademia) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from academia where cnpj = ? and id is distinct from ?)",
                Boolean.class, cnpj, excetoAcademia));
    }

    @Override
    public void inserir(Academia a) {
        var d = a.dados();
        jdbc.update("""
                insert into academia (id, nome, cnpj, endereco, cidade, estado, cep, telefone, email, status)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, a.id(), d.nome(), d.cnpj(), d.endereco(), d.cidade(), d.estado(), d.cep(), d.telefone(),
                d.email(), a.status().name());
    }

    @Override
    public void atualizar(Academia a) {
        var d = a.dados();
        jdbc.update("""
                update academia set nome = ?, cnpj = ?, endereco = ?, cidade = ?, estado = ?, cep = ?, telefone = ?,
                                    email = ?, status = ?, atualizado_em = now()
                 where id = ?
                """, d.nome(), d.cnpj(), d.endereco(), d.cidade(), d.estado(), d.cep(), d.telefone(), d.email(),
                a.status().name(), a.id());
    }
}
