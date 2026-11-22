package com.locgym.identidade.dominio;

import java.util.Objects;
import java.util.UUID;

/** Conta de login, global ao sistema. Perfis vêm dos vínculos com cada academia. */
public class Usuario {

    private final UUID id;
    private final Email email;
    private final String nome;
    private String senhaHash;
    private boolean deveTrocarSenha;

    public Usuario(UUID id, Email email, String nome, String senhaHash, boolean deveTrocarSenha) {
        this.id = Objects.requireNonNull(id);
        this.email = Objects.requireNonNull(email);
        this.nome = Objects.requireNonNull(nome);
        this.senhaHash = Objects.requireNonNull(senhaHash);
        this.deveTrocarSenha = deveTrocarSenha;
    }

    public static Usuario novo(Email email, String nome, String senhaHash, boolean deveTrocarSenha) {
        return new Usuario(UUID.randomUUID(), email, nome.strip(), senhaHash, deveTrocarSenha);
    }

    public void definirSenha(String novoHash) {
        this.senhaHash = Objects.requireNonNull(novoHash);
        this.deveTrocarSenha = false;
    }

    public UUID id() { return id; }
    public Email email() { return email; }
    public String nome() { return nome; }
    public String senhaHash() { return senhaHash; }
    public boolean deveTrocarSenha() { return deveTrocarSenha; }
}
