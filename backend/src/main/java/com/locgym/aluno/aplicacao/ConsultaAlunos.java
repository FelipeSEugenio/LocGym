package com.locgym.aluno.aplicacao;

import com.locgym.aluno.Alunos;
import com.locgym.aluno.dominio.AlunoRepositorio;
import com.locgym.aluno.dominio.StatusAluno;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ConsultaAlunos implements Alunos {

    private final AlunoRepositorio alunos;

    ConsultaAlunos(AlunoRepositorio alunos) {
        this.alunos = alunos;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SituacaoAluno> situacao(UUID alunoId) {
        return alunos.porId(alunoId)
                .map(a -> new SituacaoAluno(a.id(), a.dados().nome(), a.status() == StatusAluno.ATIVO));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> nomes(Collection<UUID> alunoIds) {
        return alunos.nomes(alunoIds);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> idDoUsuario(UUID usuarioId) {
        return alunos.porUsuario(usuarioId).map(a -> a.id());
    }
}
