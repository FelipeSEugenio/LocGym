package com.locgym.frequencia.infraestrutura;

import com.locgym.compartilhado.tenancy.EntidadeDeTenant;
import com.locgym.frequencia.dominio.Checkin;
import com.locgym.frequencia.dominio.Origem;
import com.locgym.frequencia.dominio.Pontualidade;
import com.locgym.frequencia.dominio.StatusCheckin;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "checkin")
class CheckinJpa extends EntidadeDeTenant {

    @Id
    private UUID id;
    private UUID alunoId;
    private UUID aulaId;
    private LocalDate data;
    private LocalTime hora;
    private Instant registradoEm;
    @Enumerated(EnumType.STRING)
    private Origem origem;
    @Enumerated(EnumType.STRING)
    private Pontualidade pontualidade;
    @Enumerated(EnumType.STRING)
    private StatusCheckin status;
    private String motivo;
    private UUID substituidoPor;
    private UUID registradoPor;

    @Version
    private long version;

    protected CheckinJpa() {}

    static CheckinJpa novo(Checkin c) {
        CheckinJpa e = new CheckinJpa();
        e.id = c.id();
        e.alunoId = c.alunoId();
        e.aulaId = c.aulaId();
        e.data = c.data();
        e.hora = c.hora();
        e.registradoEm = c.registradoEm();
        e.origem = c.origem();
        e.pontualidade = c.pontualidade();
        e.registradoPor = c.registradoPor();
        e.copiar(c);
        return e;
    }

    /** Só o que muda depois de criado: status, motivo e substituto. */
    void copiar(Checkin c) {
        this.status = c.status();
        this.motivo = c.motivo();
        this.substituidoPor = c.substituidoPor();
    }

    Checkin paraDominio() {
        return new Checkin(id, alunoId, aulaId, data, hora, registradoEm, origem, pontualidade, status, motivo,
                substituidoPor, registradoPor);
    }
}
