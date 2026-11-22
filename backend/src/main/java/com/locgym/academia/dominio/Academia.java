package com.locgym.academia.dominio;

import java.util.Objects;
import java.util.UUID;

public class Academia {

    private final UUID id;
    private DadosDaAcademia dados;
    private StatusAcademia status;

    public Academia(UUID id, DadosDaAcademia dados, StatusAcademia status) {
        this.id = Objects.requireNonNull(id);
        this.dados = Objects.requireNonNull(dados);
        this.status = Objects.requireNonNull(status);
    }

    public static Academia nova(DadosDaAcademia dados) {
        return new Academia(UUID.randomUUID(), dados, StatusAcademia.ATIVA);
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
}
