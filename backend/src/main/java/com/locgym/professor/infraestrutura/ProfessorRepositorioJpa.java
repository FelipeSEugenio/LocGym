package com.locgym.professor.infraestrutura;

import com.locgym.compartilhado.paginacao.Pagina;
import com.locgym.professor.dominio.Professor;
import com.locgym.professor.dominio.ProfessorRepositorio;
import com.locgym.professor.dominio.StatusProfessor;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
class ProfessorRepositorioJpa implements ProfessorRepositorio {

    private final ProfessorJpaDados dados;

    ProfessorRepositorioJpa(ProfessorJpaDados dados) {
        this.dados = dados;
    }

    @Override
    public Pagina<Professor> buscar(String buscaOuNulo, StatusProfessor statusOuNulo, int pagina, int tamanho) {
        String padrao = buscaOuNulo == null || buscaOuNulo.isBlank()
                ? null : "%" + buscaOuNulo.strip().toLowerCase(Locale.ROOT) + "%";
        var resultado = dados.buscar(padrao, statusOuNulo, PageRequest.of(pagina, tamanho));
        return new Pagina<>(resultado.getContent().stream().map(ProfessorJpa::paraDominio).toList(),
                pagina, tamanho, resultado.getTotalElements());
    }

    @Override
    public Optional<Professor> porId(UUID id) {
        return dados.findById(id).map(ProfessorJpa::paraDominio);
    }

    @Override
    public java.util.Map<UUID, String> nomes(java.util.Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return java.util.Map.of();
        }
        return dados.findAllById(java.util.Set.copyOf(ids)).stream().map(ProfessorJpa::paraDominio)
                .collect(java.util.stream.Collectors.toMap(Professor::id, p -> p.dados().nome()));
    }

    @Override
    public void salvar(Professor professor) {
        ProfessorJpa entidade = dados.findById(professor.id()).orElseGet(() -> new ProfessorJpa(professor.id()));
        entidade.copiar(professor);
        dados.save(entidade);
    }
}
