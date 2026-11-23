package com.locgym.matricula.infraestrutura;

import com.locgym.matricula.dominio.StatusMatricula;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Consultas filtradas automaticamente pela academia ativa (@TenantId) e protegidas por RLS. */
interface MatriculaJpaDados extends JpaRepository<MatriculaJpa, UUID> {

    Optional<MatriculaJpa> findFirstByAlunoIdAndStatusNot(UUID alunoId, StatusMatricula status);

    List<MatriculaJpa> findByStatusOrderById(StatusMatricula status);
}
