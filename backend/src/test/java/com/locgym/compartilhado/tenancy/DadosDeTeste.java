package com.locgym.compartilhado.tenancy;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.jdbc.core.JdbcTemplate;

/** Inserções diretas (como superusuário) para preparar cenários de teste. */
public final class DadosDeTeste {

    /** Hash BCrypt de "senha-forte-123". */
    public static final String HASH_SENHA_PADRAO = "$2a$10$eDm8HB8gpnIm9YC0wKPIredNbQVSo6nh5a2IEoLdDoAWxK.6pBjx6";

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
        String cpf = com.locgym.apoio.Cenarios.cpfValido();
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

    /** Matrícula ATIVA mensal de R$ 150,00 iniciada em {@code inicio} (vigente por um mês). */
    public static UUID matricula(JdbcTemplate su, UUID academia, UUID aluno, UUID plano, java.time.LocalDate inicio) {
        UUID id = UUID.randomUUID();
        su.update("""
                insert into matricula (id, academia_id, aluno_id, plano_id, valor, periodicidade, data_inicio,
                                       data_base, vigente_ate, status)
                values (?, ?, ?, ?, 150.00, 'MENSAL', ?, ?, ?, 'ATIVA')
                """, id, academia, aluno, plano, inicio, inicio, inicio.plusMonths(1));
        return id;
    }

    /** Cobrança PENDENTE do período mensal que começa em {@code inicio}. */
    public static UUID cobranca(JdbcTemplate su, UUID academia, UUID matricula, UUID aluno, java.time.LocalDate inicio) {
        UUID id = UUID.randomUUID();
        su.update("""
                insert into pagamento (id, academia_id, matricula_id, aluno_id, valor, vencimento, periodo_inicio,
                                       periodo_fim, status)
                values (?, ?, ?, ?, 150.00, ?, ?, ?, 'PENDENTE')
                """, id, academia, matricula, aluno, inicio, inicio, inicio.plusMonths(1));
        return id;
    }

    public static UUID professor(JdbcTemplate su, UUID academia, String status) {
        UUID id = UUID.randomUUID();
        su.update("insert into professor (id, academia_id, nome, status) values (?, ?, ?, ?)",
                id, academia, "Prof " + id.toString().substring(0, 6), status);
        return id;
    }

    /** Horário ATIVO; {@code diaSemana} ISO (1 = segunda). */
    public static UUID horario(JdbcTemplate su, UUID academia, UUID professor, int diaSemana,
                               java.time.LocalTime inicio, java.time.LocalTime fim, String tipo) {
        UUID id = UUID.randomUUID();
        su.update("""
                insert into horario (id, academia_id, professor_id, dia_semana, inicio, fim, tipo, status)
                values (?, ?, ?, ?, ?, ?, ?, 'ATIVO')
                """, id, academia, professor, diaSemana, inicio, fim, tipo);
        return id;
    }

    /** Aula do horário na data, copiando início, fim, professor e tipo. */
    public static UUID aula(JdbcTemplate su, UUID horario, java.time.LocalDate data) {
        UUID id = UUID.randomUUID();
        su.update("""
                insert into aula (id, academia_id, horario_id, data, inicio, fim, professor_id, tipo)
                select ?, academia_id, id, ?, inicio, fim, professor_id, tipo from horario where id = ?
                """, id, data, horario);
        return id;
    }

    /** Check-in ATIVO por QR; {@code aula} nula = livre (sem pontualidade). */
    public static UUID checkin(JdbcTemplate su, UUID academia, UUID aluno, UUID aula, java.time.LocalDate data,
                               java.time.LocalTime hora) {
        UUID id = UUID.randomUUID();
        su.update("""
                insert into checkin (id, academia_id, aluno_id, aula_id, data, hora, registrado_em, origem,
                                     pontualidade, status)
                values (?, ?, ?, ?, ?, ?, now(), 'QR', ?, 'ATIVO')
                """, id, academia, aluno, aula, data, hora, aula == null ? null : "PONTUAL");
        return id;
    }
}
