package com.locgym.plano.dominio;

import com.locgym.compartilhado.erros.ValorInvalidoException;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Plano de uma academia. Nunca é apagado — só inativado (matrículas futuras referenciam). */
public class Plano {

    private final UUID id;
    private String nome;
    private String descricao;
    private BigDecimal valor;
    private Periodicidade periodicidade;
    private StatusPlano status;

    public Plano(UUID id, String nome, String descricao, BigDecimal valor, Periodicidade periodicidade, StatusPlano status) {
        this.id = Objects.requireNonNull(id);
        this.status = Objects.requireNonNull(status);
        definir(nome, descricao, valor, periodicidade);
    }

    public static Plano novo(String nome, String descricao, BigDecimal valor, Periodicidade periodicidade) {
        return new Plano(UUID.randomUUID(), nome, descricao, valor, periodicidade, StatusPlano.ATIVO);
    }

    public void atualizar(String nome, String descricao, BigDecimal valor, Periodicidade periodicidade) {
        definir(nome, descricao, valor, periodicidade);
    }

    /** @return {@code true} se o status mudou. */
    public boolean alterarStatus(StatusPlano novo) {
        if (status == Objects.requireNonNull(novo)) {
            return false;
        }
        status = novo;
        return true;
    }

    private void definir(String nome, String descricao, BigDecimal valor, Periodicidade periodicidade) {
        if (nome == null || nome.isBlank() || nome.strip().length() > 100) {
            throw new ValorInvalidoException("nome", "Informe um nome de até 100 caracteres");
        }
        if (valor == null || valor.signum() <= 0 || valor.stripTrailingZeros().scale() > 2) {
            throw new ValorInvalidoException("valor", "O valor deve ser maior que zero, com até 2 casas decimais");
        }
        this.nome = nome.strip();
        this.descricao = descricao == null || descricao.isBlank() ? null : descricao.strip();
        this.valor = valor.setScale(2);
        this.periodicidade = Objects.requireNonNull(periodicidade);
    }

    public UUID id() { return id; }
    public String nome() { return nome; }
    public String descricao() { return descricao; }
    public BigDecimal valor() { return valor; }
    public Periodicidade periodicidade() { return periodicidade; }
    public StatusPlano status() { return status; }
}
