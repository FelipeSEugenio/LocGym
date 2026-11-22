package com.locgym.aluno.infraestrutura;

import com.locgym.aluno.dominio.Aluno;
import com.locgym.aluno.dominio.Cpf;
import com.locgym.aluno.dominio.StatusAluno;
import com.locgym.compartilhado.tenancy.EntidadeDeTenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "aluno")
class AlunoJpa extends EntidadeDeTenant {

    @Id
    private UUID id;

    @Column(updatable = false)
    private UUID usuarioId;

    /** E-mail de login (tabela global usuario), somente leitura aqui. */
    @Formula("(select u.email from usuario u where u.id = usuario_id)")
    private String email;

    private String nome;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 11)
    private String cpf;

    private String telefone;
    private LocalDate dataNascimento;
    private String endereco;
    private String cidade;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 2)
    private String estado;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 8)
    private String cep;

    @Enumerated(EnumType.STRING)
    private StatusAluno status;

    protected AlunoJpa() {}

    AlunoJpa(UUID id, UUID usuarioId) {
        this.id = id;
        this.usuarioId = usuarioId;
    }

    void copiar(Aluno a) {
        var d = a.dados();
        this.nome = d.nome();
        this.cpf = d.cpf().digitos();
        this.telefone = d.telefone();
        this.dataNascimento = d.dataNascimento();
        this.endereco = d.endereco();
        this.cidade = d.cidade();
        this.estado = d.estado();
        this.cep = d.cep();
        this.status = a.status();
    }

    Aluno paraDominio() {
        return new Aluno(id, usuarioId, email, new Aluno.Dados(nome, new Cpf(cpf), telefone, dataNascimento,
                endereco, cidade, estado, cep), status);
    }
}
