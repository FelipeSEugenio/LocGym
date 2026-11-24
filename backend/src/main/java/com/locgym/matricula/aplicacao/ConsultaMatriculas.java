package com.locgym.matricula.aplicacao;

import com.locgym.matricula.Matriculas;
import com.locgym.matricula.dominio.MatriculaRepositorio;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ConsultaMatriculas implements Matriculas {

    private final MatriculaRepositorio matriculas;

    ConsultaMatriculas(MatriculaRepositorio matriculas) {
        this.matriculas = matriculas;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SituacaoMatricula> vigenteDoAluno(UUID alunoId) {
        return matriculas.naoCanceladaDoAluno(alunoId)
                .map(m -> new SituacaoMatricula(m.id(), m.status().name(), m.dataInicio()));
    }
}
