package com.locgym.plano.infraestrutura;

import com.locgym.compartilhado.tenancy.EntidadeDeTenant;
import com.locgym.plano.dominio.Periodicidade;
import com.locgym.plano.dominio.Plano;
import com.locgym.plano.dominio.StatusPlano;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "plano")
class PlanoJpa extends EntidadeDeTenant {

    @Id
    private UUID id;
    private String nome;
    private String descricao;
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    private Periodicidade periodicidade;

    @Enumerated(EnumType.STRING)
    private StatusPlano status;

    protected PlanoJpa() {}

    PlanoJpa(UUID id) {
        this.id = id;
    }

    void copiar(Plano p) {
        this.nome = p.nome();
        this.descricao = p.descricao();
        this.valor = p.valor();
        this.periodicidade = p.periodicidade();
        this.status = p.status();
    }

    Plano paraDominio() {
        return new Plano(id, nome, descricao, valor, periodicidade, status);
    }
}
