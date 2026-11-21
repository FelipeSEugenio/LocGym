package com.locgym.compartilhado.eventos;

import java.util.UUID;

/**
 * Evento de domínio publicado no Kafka via outbox.
 * Implementações são records cujos demais componentes formam o campo {@code dados} do envelope.
 */
public interface EventoDominio {

    /** Tipo do evento, ex.: {@code checkin.registrado}. Também é o nome do tópico Kafka. */
    String tipo();

    /** Versão do contrato do evento; começa em 1. */
    int versao();

    /** Academia (tenant) do evento; {@code null} para eventos fora de tenant. */
    UUID academiaId();
}
