package com.locgym.matricula.infraestrutura;

import com.locgym.compartilhado.tenancy.EntidadeDeTenant;
import com.locgym.matricula.dominio.Matricula;
import com.locgym.matricula.dominio.StatusMatricula;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "matricula")
class MatriculaJpa extends EntidadeDeTenant {

    @Id
    private UUID id;
    private UUID alunoId;
    private UUID planoId;
    private BigDecimal valor;
    private String periodicidade;
    private LocalDate dataInicio;
    private LocalDate dataBase;
    private LocalDate vigenteAte;

    @Enumerated(EnumType.STRING)
    private StatusMatricula status;

    private LocalDate suspensaDesde;
    private String motivo;

    @Version
    private long version;

    protected MatriculaJpa() {}

    static MatriculaJpa nova(Matricula m) {
        MatriculaJpa e = new MatriculaJpa();
        e.id = m.id();
        e.alunoId = m.alunoId();
        e.planoId = m.planoId();
        e.valor = m.valor();
        e.periodicidade = m.periodicidade();
        e.dataInicio = m.dataInicio();
        e.copiar(m);
        return e;
    }

    void copiar(Matricula m) {
        this.dataBase = m.dataBase();
        this.vigenteAte = m.vigenteAte();
        this.status = m.status();
        this.suspensaDesde = m.suspensaDesde();
        this.motivo = m.motivo();
    }

    Matricula paraDominio() {
        return new Matricula(id, alunoId, planoId, valor, periodicidade, dataInicio, dataBase, vigenteAte, status,
                suspensaDesde, motivo);
    }
}
