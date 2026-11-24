package com.locgym.frequencia.dominio;

import com.locgym.compartilhado.paginacao.Pagina;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface CheckinRepositorio {

    record FiltroCheckins(UUID alunoId, UUID horarioId, LocalDate de, LocalDate ate, StatusCheckin status) {}

    /** @throws com.locgym.compartilhado.erros.RegraVioladaException {@code CHECKIN_DUPLICADO} se já há registro ativo */
    void inserir(Checkin checkin);

    Optional<Checkin> porId(UUID id);

    void salvar(Checkin checkin);

    /** Mais recentes primeiro (data e hora decrescentes). */
    Pagina<Checkin> listar(FiltroCheckins filtro, int pagina, int tamanho);
}
