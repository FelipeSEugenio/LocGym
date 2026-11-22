package com.locgym.academia.web;

import com.locgym.academia.dominio.Academia;
import java.util.UUID;

record AcademiaResposta(UUID id, String nome, String cnpj, String endereco, String cidade, String estado,
                        String cep, String telefone, String email, String status) {

    static AcademiaResposta de(Academia a) {
        var d = a.dados();
        return new AcademiaResposta(a.id(), d.nome(), d.cnpj(), d.endereco(), d.cidade(), d.estado(), d.cep(),
                d.telefone(), d.email(), a.status().name());
    }
}
