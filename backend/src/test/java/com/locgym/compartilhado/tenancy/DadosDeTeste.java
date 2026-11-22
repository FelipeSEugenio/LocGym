package com.locgym.compartilhado.tenancy;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.jdbc.core.JdbcTemplate;

/** Inserções diretas (como superusuário) para preparar cenários de teste. */
public final class DadosDeTeste {

    /** Hash BCrypt de "senha-forte-123". */
    public static final String HASH_SENHA_PADRAO = "$2a$10$Gm4mHq0Z4Sx8vH4v0Jq4pO4rL6cS3n3o9mWm0w9Dk8b5u3h1y0ZQe";

    private DadosDeTeste() {}

    public static UUID academia(JdbcTemplate su) {
        return academia(su, "ATIVA");
    }

    public static UUID academia(JdbcTemplate su, String status) {
        UUID id = UUID.randomUUID();
        String cnpj = String.format("%014d", ThreadLocalRandom.current().nextLong(1, 99_999_999_999_999L));
        su.update("""
                insert into academia (id, nome, cnpj, endereco, cidade, estado, cep, telefone, email, status)
                values (?, ?, ?, 'Rua A, 1', 'São Paulo', 'SP', '01001000', '11999990000', 'contato@academia.com', ?)
                """, id, "Academia " + id.toString().substring(0, 8), cnpj, status);
        return id;
    }

    public static UUID usuario(JdbcTemplate su, String email) {
        UUID id = UUID.randomUUID();
        su.update("insert into usuario (id, email, nome, senha_hash) values (?, ?, 'Usuário Teste', ?)",
                id, email, HASH_SENHA_PADRAO);
        return id;
    }

    public static void administrador(JdbcTemplate su, UUID usuario, UUID academia) {
        su.update("insert into administrador_academia (usuario_id, academia_id, nome) values (?, ?, 'Admin Teste')",
                usuario, academia);
    }

    public static UUID aluno(JdbcTemplate su, UUID usuario, UUID academia, String status) {
        UUID id = UUID.randomUUID();
        String cpf = String.format("%011d", ThreadLocalRandom.current().nextLong(1, 99_999_999_999L));
        su.update("""
                insert into aluno (id, academia_id, usuario_id, nome, cpf, telefone, data_nascimento,
                                   endereco, cidade, estado, cep, status)
                values (?, ?, ?, 'Aluno Teste', ?, '11988887777', date '1995-05-10',
                        'Rua B, 2', 'São Paulo', 'SP', '01001000', ?)
                """, id, academia, usuario, cpf, status);
        return id;
    }

    public static UUID plano(JdbcTemplate su, UUID academia, String nome) {
        UUID id = UUID.randomUUID();
        su.update("""
                insert into plano (id, academia_id, nome, valor, periodicidade, status)
                values (?, ?, ?, 150.00, 'MENSAL', 'ATIVO')
                """, id, academia, nome);
        return id;
    }
}
