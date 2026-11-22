package com.locgym.aluno.web;

import com.locgym.aluno.dominio.Aluno;
import com.locgym.aluno.dominio.StatusAluno;
import java.time.LocalDate;
import java.util.UUID;

record AlunoResposta(UUID id, String nome, String email, String cpf, String telefone, LocalDate dataNascimento,
                     String endereco, String cidade, String estado, String cep, StatusAluno status) {

    static AlunoResposta de(Aluno a) {
        var d = a.dados();
        return new AlunoResposta(a.id(), d.nome(), a.email(), d.cpf().digitos(), d.telefone(), d.dataNascimento(),
                d.endereco(), d.cidade(), d.estado(), d.cep(), a.status());
    }
}
