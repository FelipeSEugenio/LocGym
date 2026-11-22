package com.locgym.plano.dominio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Planos da academia ativa (o isolamento por academia é responsabilidade da infraestrutura). */
public interface PlanoRepositorio {

    List<Plano> listar(StatusPlano statusOuNulo);

    Optional<Plano> porId(UUID id);

    boolean nomeEmUso(String nome, UUID excetoPlano);

    void salvar(Plano plano);
}
