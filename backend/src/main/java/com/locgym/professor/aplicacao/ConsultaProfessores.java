package com.locgym.professor.aplicacao;

import com.locgym.professor.Professores;
import com.locgym.professor.dominio.ProfessorRepositorio;
import com.locgym.professor.dominio.StatusProfessor;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ConsultaProfessores implements Professores {

    private final ProfessorRepositorio professores;

    ConsultaProfessores(ProfessorRepositorio professores) {
        this.professores = professores;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SituacaoProfessor> situacao(UUID professorId) {
        return professores.porId(professorId)
                .map(p -> new SituacaoProfessor(p.id(), p.dados().nome(), p.status() == StatusProfessor.ATIVO));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> nomes(Collection<UUID> professorIds) {
        return professores.nomes(professorIds);
    }
}
