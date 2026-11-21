package com.locgym.compartilhado.eventos.infraestrutura;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Configuração do relay do outbox ({@code locgym.outbox.*}). */
@ConfigurationProperties("locgym.outbox")
record OutboxPropriedades(
        @DefaultValue("true") boolean relayHabilitado,
        @DefaultValue("1000") long intervaloMs,
        @DefaultValue("100") int lote,
        @DefaultValue("5000") long timeoutEnvioMs) {}
