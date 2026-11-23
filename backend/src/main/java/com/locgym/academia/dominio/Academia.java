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

    public Academia(UUID id, DadosDaAcademia dados, StatusAcademia status, ParametrosCobranca parametrosCobranca) {
        this.id = Objects.requireNonNull(id);
        this.dados = Objects.requireNonNull(dados);
        this.status = Objects.requireNonNull(status);
        this.parametrosCobranca = Objects.requireNonNull(parametrosCobranca);
    }

    public static Academia nova(DadosDaAcademia dados) {
        return new Academia(UUID.randomUUID(), dados, StatusAcademia.ATIVA, ParametrosCobranca.PADRAO);
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
}
