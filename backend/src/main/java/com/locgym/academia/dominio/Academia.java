package com.locgym.academia.dominio;

import com.locgym.academia.ParametrosCobranca;
import com.locgym.compartilhado.erros.ValorInvalidoException;
import java.util.Objects;
import java.util.UUID;

public class Academia {

    private final UUID id;
    private DadosDaAcademia dados;
    private StatusAcademia status;
    private ParametrosCobranca parametrosCobranca;
    private int toleranciaPontualidade;

    public static final int TOLERANCIA_PADRAO = 10;

    public Academia(UUID id, DadosDaAcademia dados, StatusAcademia status, ParametrosCobranca parametrosCobranca,
                    int toleranciaPontualidade) {
        this.id = Objects.requireNonNull(id);
        this.dados = Objects.requireNonNull(dados);
        this.status = Objects.requireNonNull(status);
        this.parametrosCobranca = Objects.requireNonNull(parametrosCobranca);
        this.toleranciaPontualidade = toleranciaPontualidade;
    }

    public static Academia nova(DadosDaAcademia dados) {
        return new Academia(UUID.randomUUID(), dados, StatusAcademia.ATIVA, ParametrosCobranca.PADRAO, TOLERANCIA_PADRAO);
    }

    /** @return {@code true} se algum parâmetro mudou. */
    public boolean alterarParametrosCobranca(ParametrosCobranca novos) {
        if (novos.diasAntecedencia() < 1 || novos.diasAntecedencia() > 30) {
            throw new ValorInvalidoException("diasAntecedenciaCobranca", "Informe de 1 a 30 dias");
        }
        if (novos.diasCarencia() < 0 || novos.diasCarencia() > 60) {
            throw new ValorInvalidoException("diasCarencia", "Informe de 0 a 60 dias");
        }
        if (parametrosCobranca.equals(novos)) {
            return false;
        }
        parametrosCobranca = novos;
        return true;
    }

    /** Minutos após o início da aula em que o check-in ainda é pontual (0 a 60). @return {@code true} se mudou. */
    public boolean alterarToleranciaPontualidade(int minutos) {
        if (minutos < 0 || minutos > 60) {
            throw new ValorInvalidoException("toleranciaPontualidade", "Informe de 0 a 60 minutos");
        }
        if (toleranciaPontualidade == minutos) {
            return false;
        }
        toleranciaPontualidade = minutos;
        return true;
    }

    public void atualizar(DadosDaAcademia novos) {
        this.dados = Objects.requireNonNull(novos);
    }

    /** @return {@code true} se o status mudou. */
    public boolean alterarStatus(StatusAcademia novo) {
        if (status == Objects.requireNonNull(novo)) {
            return false;
        }
        status = novo;
        return true;
    }

    public UUID id() { return id; }
    public DadosDaAcademia dados() { return dados; }
    public StatusAcademia status() { return status; }
    public ParametrosCobranca parametrosCobranca() { return parametrosCobranca; }
    public int toleranciaPontualidade() { return toleranciaPontualidade; }
}
