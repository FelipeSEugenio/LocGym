package com.locgym.identidade;

import java.util.UUID;

/**
 * Porta implementada pelo módulo academia: cria uma academia ATIVA já com o primeiro administrador.
 * Mantém a dependência no sentido academia → identidade.
 */
public interface CriadorDeAcademia {

    /**
     * @throws com.locgym.compartilhado.erros.RegraVioladaException {@code CNPJ_JA_CADASTRADO}
     */
    UUID criarComAdministrador(NovaAcademia dados, UUID usuarioId, String nomeAdministrador);

    record NovaAcademia(String nome, String cnpj, String endereco, String cidade, String estado,
                        String cep, String telefone, String email) {}
}
