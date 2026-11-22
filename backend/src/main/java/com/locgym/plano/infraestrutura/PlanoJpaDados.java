package com.locgym.plano.infraestrutura;

import com.locgym.plano.dominio.StatusPlano;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Consultas filtradas automaticamente pela academia ativa (@TenantId) e protegidas por RLS. */
interface PlanoJpaDados extends JpaRepository<PlanoJpa, UUID> {

    List<PlanoJpa> findAllByOrderByNome();

    List<PlanoJpa> findAllByStatusOrderByNome(StatusPlano status);

    boolean existsByNome(String nome);

    boolean existsByNomeAndIdNot(String nome, UUID id);
}
