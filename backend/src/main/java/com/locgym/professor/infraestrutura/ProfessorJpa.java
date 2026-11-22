package com.locgym.professor.infraestrutura;

import com.locgym.compartilhado.tenancy.EntidadeDeTenant;
import com.locgym.professor.dominio.Professor;
import com.locgym.professor.dominio.StatusProfessor;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "professor")
class ProfessorJpa extends EntidadeDeTenant {

    @Id
    private UUID id;
    private String nome;
    private String email;
    private String telefone;
    private LocalDate dataNascimento;
    private String especialidade;

    @Enumerated(EnumType.STRING)
    private StatusProfessor status;

    protected ProfessorJpa() {}

    ProfessorJpa(UUID id) {
        this.id = id;
    }

    void copiar(Professor p) {
        var d = p.dados();
        this.nome = d.nome();
        this.email = d.email();
        this.telefone = d.telefone();
        this.dataNascimento = d.dataNascimento();
        this.especialidade = d.especialidade();
        this.status = p.status();
    }

    Professor paraDominio() {
        return new Professor(id, new Professor.Dados(nome, email, telefone, dataNascimento, especialidade), status);
    }
}
