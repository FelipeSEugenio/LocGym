package com.locgym.plano.aplicacao;

import com.locgym.compartilhado.auditoria.AcaoAuditada;
import com.locgym.compartilhado.auditoria.Auditoria;
import com.locgym.compartilhado.erros.NaoEncontradoException;
import com.locgym.compartilhado.erros.RegraVioladaException;
import com.locgym.plano.dominio.Periodicidade;
import com.locgym.plano.dominio.Plano;
import com.locgym.plano.dominio.PlanoRepositorio;
import com.locgym.plano.dominio.StatusPlano;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicoPlanos {

    private final PlanoRepositorio planos;
    private final Auditoria auditoria;

    public ServicoPlanos(PlanoRepositorio planos, Auditoria auditoria) {
        this.planos = planos;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public List<Plano> listar(StatusPlano statusOuNulo) {
        return planos.listar(statusOuNulo);
    }

    @Transactional(readOnly = true)
    public Plano buscar(UUID id) {
        return planos.porId(id).orElseThrow(() -> new NaoEncontradoException("Plano não encontrado"));
    }

    @Transactional
    public Plano criar(String nome, String descricao, BigDecimal valor, Periodicidade periodicidade) {
        Plano plano = Plano.novo(nome, descricao, valor, periodicidade);
        exigirNomeLivre(plano.nome(), null);
        planos.salvar(plano);
        return plano;
    }

    @Transactional
    public Plano atualizar(UUID id, String nome, String descricao, BigDecimal valor, Periodicidade periodicidade) {
        Plano plano = buscar(id);
        plano.atualizar(nome, descricao, valor, periodicidade);
        exigirNomeLivre(plano.nome(), id);
        planos.salvar(plano);
        return plano;
    }

    @Transactional
    public Plano alterarStatus(UUID id, StatusPlano novo) {
        Plano plano = buscar(id);
        StatusPlano anterior = plano.status();
        if (plano.alterarStatus(novo)) {
            planos.salvar(plano);
            auditoria.registrar(AcaoAuditada.PLANO_STATUS_ALTERADO, "plano", id,
                    Map.of("status", anterior.name()), Map.of("status", novo.name()));
        }
        return plano;
    }

    private void exigirNomeLivre(String nome, UUID excetoPlano) {
        if (planos.nomeEmUso(nome, excetoPlano)) {
            throw new RegraVioladaException("PLANO_JA_EXISTE", "Já existe um plano com este nome");
        }
    }
}
