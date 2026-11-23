package com.locgym.academia;

import java.util.List;
import java.util.UUID;

/** API pública do módulo de academias para rotinas de outros módulos ({@code academia} é tabela global). */
public interface Academias {

    /** Ids das academias com status ATIVA. */
    List<UUID> ativas();

    /** @throws com.locgym.compartilhado.erros.NaoEncontradoException se a academia não existe */
    ParametrosCobranca parametrosCobranca(UUID academiaId);
}
