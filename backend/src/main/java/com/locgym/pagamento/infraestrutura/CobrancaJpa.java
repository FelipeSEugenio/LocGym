package com.locgym.pagamento.infraestrutura;

import com.locgym.compartilhado.tenancy.EntidadeDeTenant;
import com.locgym.pagamento.dominio.Cobranca;
import com.locgym.pagamento.dominio.FormaPagamento;
import com.locgym.pagamento.dominio.StatusCobranca;
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
@Table(name = "pagamento")
class CobrancaJpa extends EntidadeDeTenant {

    @Id
    private UUID id;
    private UUID matriculaId;
    private UUID alunoId;
    private BigDecimal valor;
    private LocalDate vencimento;
    private LocalDate periodoInicio;
    private LocalDate periodoFim;

    @Enumerated(EnumType.STRING)
    private StatusCobranca status;

    private LocalDate dataPagamento;

    @Enumerated(EnumType.STRING)
    private FormaPagamento forma;

    private String observacoes;
    private String motivoCancelamento;

    @Version
    private long version;

    protected CobrancaJpa() {}

    void copiar(Cobranca c) {
        this.vencimento = c.vencimento();
        this.periodoInicio = c.periodoInicio();
        this.periodoFim = c.periodoFim();
        this.status = c.status();
        this.dataPagamento = c.dataPagamento();
        this.forma = c.forma();
        this.observacoes = c.observacoes();
        this.motivoCancelamento = c.motivoCancelamento();
    }

    Cobranca paraDominio() {
        return new Cobranca(id, matriculaId, alunoId, valor, vencimento, periodoInicio, periodoFim, status,
                dataPagamento, forma, observacoes, motivoCancelamento);
    }
}
