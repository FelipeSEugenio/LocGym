package com.locgym.compartilhado.eventos.infraestrutura;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface OutboxRepositorio extends JpaRepository<OutboxEvento, UUID> {

    /** Lock exclusivo do relay durante a transação; {@code false} se outro relay estiver ativo. */
    @Query(value = "select pg_try_advisory_xact_lock(:chave)", nativeQuery = true)
    boolean tentarLockDoRelay(long chave);

    @Query(value = "select * from outbox_evento where publicado_em is null order by sequencia limit :limite",
            nativeQuery = true)
    List<OutboxEvento> buscarPendentesParaPublicar(int limite);
}
