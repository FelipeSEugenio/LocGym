package com.locgym.agenda.infraestrutura;

import com.locgym.agenda.dominio.StatusHorario;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface HorarioJpaDados extends JpaRepository<HorarioJpa, UUID> {

    @Query("""
            select h from HorarioJpa h
             where (:dia is null or h.diaSemana = :dia)
               and (:status is null or h.status = :status)
             order by h.diaSemana, h.inicio, h.tipo, h.id
            """)
    List<HorarioJpa> listar(Short dia, StatusHorario status);
}
