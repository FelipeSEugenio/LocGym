package com.locgym.aluno.infraestrutura;

import com.locgym.aluno.dominio.StatusAluno;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface AlunoJpaDados extends JpaRepository<AlunoJpa, UUID> {

    @Query("""
            select a from AlunoJpa a
             where (:status is null or a.status = :status)
               and (:padrao is null
                    or lower(a.nome) like :padrao
                    or lower(a.email) like :padrao
                    or (:padraoCpf is not null and a.cpf like :padraoCpf))
             order by lower(a.nome), a.id
            """)
    Page<AlunoJpa> buscar(String padrao, String padraoCpf, StatusAluno status, Pageable pageable);

    Optional<AlunoJpa> findByUsuarioId(UUID usuarioId);

    boolean existsByUsuarioId(UUID usuarioId);

    boolean existsByCpf(String cpf);

    boolean existsByCpfAndIdNot(String cpf, UUID id);

    @org.springframework.data.jpa.repository.Query("select a.id as id, a.nome as nome from AlunoJpa a where a.id in :ids")
    java.util.List<IdNome> nomesPorIds(java.util.Collection<UUID> ids);

    interface IdNome {
        UUID getId();
        String getNome();
    }
}
