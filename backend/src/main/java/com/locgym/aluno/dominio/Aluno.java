package com.locgym.aluno.dominio;

import com.locgym.compartilhado.erros.ValorInvalidoException;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Aluno de uma academia. Cada academia tem o seu registro; o login (e-mail) é global e não é editado
 * por aqui, porque mudaria o acesso do aluno em outras academias.
 */
public class Aluno {

    public record Dados(String nome, Cpf cpf, String telefone, LocalDate dataNascimento, String endereco,
                        String cidade, String estado, String cep) {}

    private final UUID id;
    private final UUID usuarioId;
    private final String email;
    private Dados dados;
    private StatusAluno status;

    public Aluno(UUID id, UUID usuarioId, String email, Dados dados, StatusAluno status) {
        this.id = Objects.requireNonNull(id);
        this.usuarioId = Objects.requireNonNull(usuarioId);
        this.email = email;
        this.status = Objects.requireNonNull(status);
        this.dados = validar(dados);
    }

    public static Aluno novo(UUID usuarioId, String email, Dados dados) {
        return new Aluno(UUID.randomUUID(), usuarioId, email, dados, StatusAluno.ATIVO);
    }

    public void atualizar(Dados novos) {
        this.dados = validar(novos);
    }

    /** @return {@code true} se o status mudou. */
    public boolean alterarStatus(StatusAluno novo) {
        if (status == Objects.requireNonNull(novo)) {
            return false;
        }
        status = novo;
        return true;
    }

    private static Dados validar(Dados d) {
        Objects.requireNonNull(d.cpf(), "cpf");
        exigir(d.nome(), "nome", 150);
        exigir(d.telefone(), "telefone", 20);
        exigir(d.endereco(), "endereco", 200);
        exigir(d.cidade(), "cidade", 100);
        if (d.estado() == null || !d.estado().matches("[A-Z]{2}")) {
            throw new ValorInvalidoException("estado", "UF inválida");
        }
        if (d.cep() == null || !d.cep().matches("\\d{8}")) {
            throw new ValorInvalidoException("cep", "CEP inválido");
        }
        if (d.dataNascimento() == null || d.dataNascimento().isAfter(LocalDate.now())) {
            throw new ValorInvalidoException("dataNascimento", "Data de nascimento inválida");
        }
        return new Dados(d.nome().strip(), d.cpf(), d.telefone().strip(), d.dataNascimento(), d.endereco().strip(),
                d.cidade().strip(), d.estado(), d.cep());
    }

    private static void exigir(String valor, String campo, int maximo) {
        if (valor == null || valor.isBlank() || valor.strip().length() > maximo) {
            throw new ValorInvalidoException(campo, "Campo obrigatório (até " + maximo + " caracteres)");
        }
    }

    public UUID id() { return id; }
    public UUID usuarioId() { return usuarioId; }
    public String email() { return email; }
    public Dados dados() { return dados; }
    public StatusAluno status() { return status; }
}
