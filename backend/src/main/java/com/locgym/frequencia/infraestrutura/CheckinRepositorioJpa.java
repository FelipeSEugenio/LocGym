package com.locgym.frequencia.infraestrutura;

import com.locgym.compartilhado.erros.RegraVioladaException;
import com.locgym.compartilhado.paginacao.Pagina;
import com.locgym.frequencia.dominio.Checkin;
import com.locgym.frequencia.dominio.CheckinRepositorio;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
class CheckinRepositorioJpa implements CheckinRepositorio {

    private static final String PREFIXO_INDICE_UNICO = "ux_checkin_";

    private final CheckinJpaDados dados;

    CheckinRepositorioJpa(CheckinJpaDados dados) {
        this.dados = dados;
    }

    @Override
    public void inserir(Checkin checkin) {
        try {
            dados.saveAndFlush(CheckinJpa.novo(checkin));
        } catch (DataIntegrityViolationException e) {
            if (String.valueOf(e.getMostSpecificCause().getMessage()).contains(PREFIXO_INDICE_UNICO)) {
                throw new RegraVioladaException("CHECKIN_DUPLICADO",
                        checkin.livre() ? "Check-in já registrado hoje" : "Check-in já registrado nesta aula");
            }
            throw e;
        }
    }

    @Override
    public Optional<Checkin> porId(UUID id) {
        return dados.findById(id).map(CheckinJpa::paraDominio);
    }

    /** Flush imediato: conflito de versão vira erro aqui, dentro da transação. */
    @Override
    public void salvar(Checkin checkin) {
        CheckinJpa e = dados.findById(checkin.id())
                .orElseThrow(() -> new IllegalStateException("Check-in inexistente: " + checkin.id()));
        e.copiar(checkin);
        dados.saveAndFlush(e);
    }

    @Override
    public Pagina<Checkin> listar(FiltroCheckins filtro, int pagina, int tamanho) {
        throw new UnsupportedOperationException("Implementado na listagem do histórico");
    }
}
