package com.locgym.compartilhado.eventos;

/** Porta de publicação de eventos de domínio. */
public interface PublicadorEventos {

    /**
     * Grava o evento no outbox, na transação corrente.
     * O evento só chega ao Kafka se a transação for confirmada.
     *
     * @throws org.springframework.transaction.IllegalTransactionStateException se não houver transação ativa
     */
    void publicar(EventoDominio evento);
}
