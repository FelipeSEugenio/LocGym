package com.locgym.compartilhado.eventos.infraestrutura;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Executa o relay periodicamente; desligável com {@code locgym.outbox.relay-habilitado=false}. */
@Component
@ConditionalOnProperty(name = "locgym.outbox.relay-habilitado", havingValue = "true", matchIfMissing = true)
class OutboxAgendador {

    private final OutboxRelay relay;

    OutboxAgendador(OutboxRelay relay) {
        this.relay = relay;
    }

    @Scheduled(fixedDelayString = "${locgym.outbox.intervalo-ms:1000}")
    void executar() {
        relay.publicarPendentes();
    }
}
