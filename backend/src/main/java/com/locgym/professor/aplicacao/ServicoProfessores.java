package com.locgym.professor.aplicacao;

import com.locgym.compartilhado.auditoria.AcaoAuditada;
import com.locgym.compartilhado.auditoria.Auditoria;
import com.locgym.compartilhado.erros.NaoEncontradoException;
import com.locgym.compartilhado.paginacao.Pagina;
import com.locgym.professor.dominio.Professor;
import com.locgym.professor.dominio.ProfessorRepositorio;
import com.locgym.professor.dominio.StatusProfessor;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicoProfessores {

    private final ProfessorRepositorio professores;
    private final Auditoria auditoria;

    public ServicoProfessores(ProfessorRepositorio professores, Auditoria auditoria) {
        this.professores = professores;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public Pagina<Professor> buscar(String busca, StatusProfessor status, int pagina, int tamanho) {
        return professores.buscar(busca, status, pagina, tamanho);
    }

    @Transactional(readOnly = true)
    public Professor buscar(UUID id) {
        return professores.porId(id).orElseThrow(() -> new NaoEncontradoException("Professor não encontrado"));
    }

    @Transactional
    public Professor criar(Professor.Dados dados) {
        Professor professor = Professor.novo(dados);
        professores.salvar(professor);
        return professor;
    }

    @Transactional
    public Professor atualizar(UUID id, Professor.Dados dados) {
        Professor professor = buscar(id);
        professor.atualizar(dados);
        professores.salvar(professor);
        return professor;
    }

    @Transactional
    public Professor alterarStatus(UUID id, StatusProfessor novo) {
        Professor professor = buscar(id);
        StatusProfessor anterior = professor.status();
        if (professor.alterarStatus(novo)) {
            professores.salvar(professor);
            auditoria.registrar(AcaoAuditada.PROFESSOR_STATUS_ALTERADO, "professor", id,
                    Map.of("status", anterior.name()), Map.of("status", novo.name()));
        }
        return professor;
    }
}
