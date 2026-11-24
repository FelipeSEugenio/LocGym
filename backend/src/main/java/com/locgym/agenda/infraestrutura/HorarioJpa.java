package com.locgym.agenda.infraestrutura;

import com.locgym.agenda.dominio.Horario;
import com.locgym.agenda.dominio.StatusHorario;
import com.locgym.compartilhado.tenancy.EntidadeDeTenant;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "horario")
class HorarioJpa extends EntidadeDeTenant {

    @Id
    private UUID id;
    private UUID professorId;
    private short diaSemana;
    private LocalTime inicio;
    private LocalTime fim;
    private String tipo;

    @Enumerated(EnumType.STRING)
    private StatusHorario status;

    @Version
    private long version;

    protected HorarioJpa() {}

    HorarioJpa(UUID id) {
        this.id = id;
    }

    void copiar(Horario h) {
        this.professorId = h.professorId();
        this.diaSemana = (short) h.diaSemana();
        this.inicio = h.inicio();
        this.fim = h.fim();
        this.tipo = h.tipo();
        this.status = h.status();
    }

    Horario paraDominio() {
        return new Horario(id, professorId, diaSemana, inicio, fim, tipo, status);
    }
}
