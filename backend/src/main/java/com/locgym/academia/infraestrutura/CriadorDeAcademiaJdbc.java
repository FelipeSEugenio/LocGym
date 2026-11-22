package com.locgym.academia.infraestrutura;

import com.locgym.compartilhado.erros.RegraVioladaException;
import com.locgym.identidade.CriadorDeAcademia;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Cria a academia (tabela global) e o vínculo do primeiro administrador. */
@Component
class CriadorDeAcademiaJdbc implements CriadorDeAcademia {

    private final JdbcTemplate jdbc;

    CriadorDeAcademiaJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID criarComAdministrador(NovaAcademia d, UUID usuarioId, String nomeAdministrador) {
        Boolean cnpjExiste = jdbc.queryForObject("select exists (select 1 from academia where cnpj = ?)",
                Boolean.class, d.cnpj());
        if (Boolean.TRUE.equals(cnpjExiste)) {
            throw new RegraVioladaException("CNPJ_JA_CADASTRADO", "CNPJ já cadastrado");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into academia (id, nome, cnpj, endereco, cidade, estado, cep, telefone, email, status)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ATIVA')
                """, id, d.nome(), d.cnpj(), d.endereco(), d.cidade(), d.estado(), d.cep(), d.telefone(), d.email());
        jdbc.update("insert into administrador_academia (usuario_id, academia_id, nome) values (?, ?, ?)",
                usuarioId, id, nomeAdministrador);
        return id;
    }
}
