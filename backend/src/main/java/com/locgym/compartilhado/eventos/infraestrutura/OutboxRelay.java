package com.locgym.compartilhado.eventos.infraestrutura;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publica no Kafka os eventos pendentes do outbox, em ordem de gravação.
 * Na primeira falha o lote é interrompido (nenhum evento posterior passa à frente de um anterior);
 * o evento com falha permanece pendente e é retentado no próximo ciclo.
 * Apenas um relay publica por vez (advisory lock do PostgreSQL).
 */
@Component
public class OutboxRelay {

    /** Identificador arbitrário e fixo do advisory lock do relay. */
    static final long LOCK_OUTBOX = 7_317_001L;

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepositorio repositorio;
    private final KafkaTemplate<String, String> kafka;
    private final OutboxPropriedades propriedades;
    private final Clock clock;

    OutboxRelay(OutboxRepositorio repositorio, KafkaTemplate<String, String> kafka,
                OutboxPropriedades propriedades, Clock clock) {
        this.repositorio = repositorio;
        this.kafka = kafka;
        this.propriedades = propriedades;
        this.clock = clock;
    }

    /** @return quantidade de eventos publicados neste ciclo */
    @Transactional
    public int publicarPendentes() {
        if (!repositorio.tentarLockDoRelay(LOCK_OUTBOX)) {
            return 0;
        }
        int publicados = 0;
        for (OutboxEvento evento : repositorio.buscarPendentesParaPublicar(propriedades.lote())) {
            try {
                enviar(evento);
                evento.marcarPublicado(clock.instant());
                publicados++;
            } catch (Exception e) {
                Throwable causa = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
                evento.registrarFalha(causa.getClass().getSimpleName() + ": " + causa.getMessage());
                log.warn("Falha ao publicar evento {} ({}), tentativa {}; lote interrompido para preservar a ordem",
                        evento.id(), evento.tipo(), evento.tentativas(), causa);
                break;
            }
        }
        return publicados;
    }

    private void enviar(OutboxEvento evento) throws Exception {
        var registro = new ProducerRecord<String, String>(evento.tipo(), evento.chave(), evento.payload());
        registro.headers().add("evento_id", evento.id().toString().getBytes(StandardCharsets.UTF_8));
        kafka.send(registro).get(propriedades.timeoutEnvioMs(), TimeUnit.MILLISECONDS);
    }
}
