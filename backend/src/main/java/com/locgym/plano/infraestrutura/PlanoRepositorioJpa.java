package com.locgym.plano.infraestrutura;

import com.locgym.plano.dominio.Plano;
import com.locgym.plano.dominio.PlanoRepositorio;
import com.locgym.plano.dominio.StatusPlano;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class PlanoRepositorioJpa implements PlanoRepositorio {

    private final PlanoJpaDados dados;

    PlanoRepositorioJpa(PlanoJpaDados dados) {
        this.dados = dados;
    }

    @Override
    public List<Plano> listar(StatusPlano statusOuNulo) {
        var lista = statusOuNulo == null ? dados.findAllByOrderByNome() : dados.findAllByStatusOrderByNome(statusOuNulo);
        return lista.stream().map(PlanoJpa::paraDominio).toList();
    }

    @Override
    public Optional<Plano> porId(UUID id) {
        return dados.findById(id).map(PlanoJpa::paraDominio);
    }

    @Override
    public boolean nomeEmUso(String nome, UUID excetoPlano) {
        return excetoPlano == null ? dados.existsByNome(nome) : dados.existsByNomeAndIdNot(nome, excetoPlano);
    }

    @Override
    public void salvar(Plano plano) {
        PlanoJpa entidade = dados.findById(plano.id()).orElseGet(() -> new PlanoJpa(plano.id()));
        entidade.copiar(plano);
        dados.save(entidade);
    }
}
