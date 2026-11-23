package com.locgym.plano;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** API pública do módulo de planos para outros módulos. Opera na academia do contexto atual. */
public interface Planos {

    /** {@code periodicidade} é o nome do enum (MENSAL, TRIMESTRAL, SEMESTRAL, ANUAL). */
    record PlanoParaMatricula(UUID id, String nome, BigDecimal valor, String periodicidade, boolean ativo) {}

    Optional<PlanoParaMatricula> paraMatricula(UUID planoId);
}
