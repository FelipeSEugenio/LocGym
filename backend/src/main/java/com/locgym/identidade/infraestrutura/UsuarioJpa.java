package com.locgym.identidade.infraestrutura;

import com.locgym.identidade.dominio.Email;
import com.locgym.identidade.dominio.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "usuario")
class UsuarioJpa {

    @Id
    private UUID id;
    private String email;
    private String nome;
    private String senhaHash;
    private boolean deveTrocarSenha;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant criadoEm;

    @UpdateTimestamp
    private Instant atualizadoEm;

    protected UsuarioJpa() {}

    static UsuarioJpa de(Usuario u) {
        UsuarioJpa e = new UsuarioJpa();
        e.id = u.id();
        e.copiar(u);
        return e;
    }

    void copiar(Usuario u) {
        this.email = u.email().valor();
        this.nome = u.nome();
        this.senhaHash = u.senhaHash();
        this.deveTrocarSenha = u.deveTrocarSenha();
    }

    Usuario paraDominio() {
        return new Usuario(id, new Email(email), nome, senhaHash, deveTrocarSenha);
    }
}
