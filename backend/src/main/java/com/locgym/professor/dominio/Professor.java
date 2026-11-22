package com.locgym.professor.dominio;

import com.locgym.compartilhado.erros.ValorInvalidoException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Professor de uma academia (sem login nesta fase). Só o nome é obrigatório. */
public class Professor {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public record Dados(String nome, String email, String telefone, LocalDate dataNascimento, String especialidade) {}

    private final UUID id;
    private Dados dados;
    private StatusProfessor status;

    public Professor(UUID id, Dados dados, StatusProfessor status) {
        this.id = Objects.requireNonNull(id);
        this.status = Objects.requireNonNull(status);
        this.dados = validar(dados);
    }

    public static Professor novo(Dados dados) {
        return new Professor(UUID.randomUUID(), dados, StatusProfessor.ATIVO);
    }

    public void atualizar(Dados novos) {
        this.dados = validar(novos);
    }

    /** @return {@code true} se o status mudou. */
    public boolean alterarStatus(StatusProfessor novo) {
        if (status == Objects.requireNonNull(novo)) {
            return false;
        }
        status = novo;
        return true;
    }

    private static Dados validar(Dados d) {
        if (d.nome() == null || d.nome().isBlank() || d.nome().strip().length() > 150) {
            throw new ValorInvalidoException("nome", "Informe um nome de até 150 caracteres");
        }
        String email = vazioParaNulo(d.email());
        if (email != null && !EMAIL.matcher(email).matches()) {
            throw new ValorInvalidoException("email", "E-mail inválido");
        }
        if (d.dataNascimento() != null && d.dataNascimento().isAfter(LocalDate.now())) {
            throw new ValorInvalidoException("dataNascimento", "Data de nascimento no futuro");
        }
        return new Dados(d.nome().strip(), email == null ? null : email.toLowerCase(Locale.ROOT),
                vazioParaNulo(d.telefone()), d.dataNascimento(), vazioParaNulo(d.especialidade()));
    }

    private static String vazioParaNulo(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    public UUID id() { return id; }
    public Dados dados() { return dados; }
    public StatusProfessor status() { return status; }
}
