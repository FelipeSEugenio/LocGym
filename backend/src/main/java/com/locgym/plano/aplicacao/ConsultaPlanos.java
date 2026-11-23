package com.locgym.plano.aplicacao;

import com.locgym.plano.Planos;
import com.locgym.plano.dominio.PlanoRepositorio;
import com.locgym.plano.dominio.StatusPlano;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ConsultaPlanos implements Planos {

    private final PlanoRepositorio planos;

    ConsultaPlanos(PlanoRepositorio planos) {
        this.planos = planos;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PlanoParaMatricula> paraMatricula(UUID planoId) {
        return planos.porId(planoId).map(p -> new PlanoParaMatricula(p.id(), p.nome(), p.valor(),
                p.periodicidade().name(), p.status() == StatusPlano.ATIVO));
    }
}
