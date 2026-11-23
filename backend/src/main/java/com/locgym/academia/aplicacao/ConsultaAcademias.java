package com.locgym.academia.aplicacao;

import com.locgym.academia.Academias;
import com.locgym.academia.ParametrosCobranca;
import com.locgym.academia.dominio.Academia;
import com.locgym.academia.dominio.Portas.AcademiaRepositorio;
import com.locgym.compartilhado.erros.NaoEncontradoException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ConsultaAcademias implements Academias {

    private final AcademiaRepositorio academias;

    ConsultaAcademias(AcademiaRepositorio academias) {
        this.academias = academias;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> ativas() {
        return academias.idsAtivas();
    }

    @Override
    @Transactional(readOnly = true)
    public ParametrosCobranca parametrosCobranca(UUID academiaId) {
        return academias.porId(academiaId).map(Academia::parametrosCobranca)
                .orElseThrow(() -> new NaoEncontradoException("Academia não encontrada"));
    }
}
