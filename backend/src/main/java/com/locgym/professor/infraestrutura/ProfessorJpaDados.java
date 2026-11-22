package com.locgym.professor.infraestrutura;

import com.locgym.professor.dominio.StatusProfessor;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ProfessorJpaDados extends JpaRepository<ProfessorJpa, UUID> {

    @Query("""
            select p from ProfessorJpa p
             where (:status is null or p.status = :status)
               and (:padrao is null or lower(p.nome) like :padrao or lower(coalesce(p.email, '')) like :padrao)
             order by lower(p.nome), p.id
            """)
    Page<ProfessorJpa> buscar(String padrao, StatusProfessor status, Pageable pageable);
}
